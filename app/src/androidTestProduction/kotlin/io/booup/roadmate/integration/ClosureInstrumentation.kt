package io.booup.roadmate.integration

import android.app.Activity
import android.app.Instrumentation
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.booup.roadmate.AndroidContextClock
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.model.*
import io.booup.roadmate.platform.*
import kotlinx.coroutines.*

/** TEST APK ONLY. Synthetic NIGHT is never installed as a Production runtime provider. */
class ClosureInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking {
                inspectNight()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val clock = AndroidContextClock()
                val logger: (String) -> Unit = { Log.i("RoadmateIntegration", it) }
                val adapter = PleosVehicleAdapter(clock, scope, { SdkVehicleReadSession(targetContext) }, log = logger,
                    restrictionAdapter = AaosDrivingRestrictionAdapter(targetContext, clock, scope, log = logger),
                    motionAdapter = AaosMotionAdapter(targetContext, clock, scope, log = logger))
                val store = VehicleContextStore(adapter, RuntimeMode.PRODUCTION, clock, scope)
                val actualEngine = DeterministicCareEngine(clock)
                val hybridEngine = DeterministicCareEngine(clock)
                try {
                    store.foreground()
                    withTimeout(35_000) {
                        while (true) {
                            val actual = store.context.value
                            val decision = actualEngine.evaluate(actual)
                            check(decision.emittedEvents.none { it.ruleId == RuleId.LIGHT }) { "Production NIGHT unknown must not fire Light P1" }
                            if (actual.lowBeam.currentValue == LowBeamState.OFF && actual.motion.currentValue == MotionState.MOVING) {
                                val now = clock.now()
                                val night = Reading.Observed(LightingCondition.NIGHT, DataMetadata(DataSource.DERIVED,
                                    now.epochMillis, Freshness.FRESH, CapabilityState.SUPPORTED, PermissionState.GRANTED,
                                    DataQuality.VALID, now.monotonicMillis)).evaluate(now, ContextFreshnessPolicies().external,
                                        actual.connection, true)
                                val hybrid = actual.copy(care = actual.care.copy(lighting = night))
                                val event = hybridEngine.evaluate(hybrid).emittedEvents.firstOrNull { it.ruleId == RuleId.LIGHT }
                                if (event != null) {
                                    check(event.priority == CarePriority.P1_SAFETY)
                                    logger("HYBRID_INTEGRATION_PASS night=TEST_ONLY_DERIVED lightSource=${actual.lowBeam.source} motionSource=${actual.motion.source} event=${event.ruleId} priority=${event.priority} productionNight=${actual.care.lighting.currentValue}")
                                    result.putString("hybrid", "HYBRID_INTEGRATION_PASS; synthetic NIGHT, not Production Night PASS")
                                    break
                                }
                            } else hybridEngine.reset()
                            delay(200)
                        }
                    }
                } finally {
                    store.background(); store.close(); actualEngine.reset(); hybridEngine.reset(); scope.cancel()
                }
            }
            finish(Activity.RESULT_OK, result)
        } catch (error: Exception) {
            Log.e("RoadmateIntegration", "INTEGRATION_FAIL", error)
            result.putString("error", error.toString()); finish(Activity.RESULT_CANCELED, result)
        }
    }

    private suspend fun inspectNight() {
        var car: Car? = null
        // Instrumentation diagnostic privilege only. Not a product permission grant or usable source.
        uiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_EXTERIOR_ENVIRONMENT)
        try {
            val manager = CompletableDeferred<CarPropertyManager>()
            withContext(Dispatchers.Main) {
                car = Car.createCar(targetContext, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                    Car.CarServiceLifecycleListener { instance, ready ->
                        if (ready) manager.complete(instance.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager)
                    })
            }
            val source = withTimeout(10_000) { manager.await() }
            withContext(Dispatchers.IO) {
                val capability = source.getCarPropertyConfig(VehiclePropertyIds.NIGHT_MODE)
                Log.i("RoadmateIntegration", "nightProbe TEST_ONLY_SHELL_IDENTITY config=$capability")
                if (capability != null) {
                    val value = source.getProperty<Boolean>(VehiclePropertyIds.NIGHT_MODE, 0)
                    Log.i("RoadmateIntegration", "nightProbe raw=${value.value} status=${value.status} timestampNanos=${value.timestamp}; mapping=NOT_VERIFIED")
                }
            }
        } catch (error: Exception) {
            Log.i("RoadmateIntegration", "nightProbe unavailable=${error.javaClass.name}; mapping=NOT_VERIFIED")
        } finally {
            withContext(Dispatchers.Main) { car?.disconnect() }
            uiAutomation.dropShellPermissionIdentity()
        }
    }
}
