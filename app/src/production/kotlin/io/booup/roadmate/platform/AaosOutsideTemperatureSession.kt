package io.booup.roadmate.platform

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.booup.roadmate.model.*
import kotlinx.coroutines.*

/** API34 public AAOS read only. An independent Car session follows the existing motion lifecycle. */
class AaosOutsideTemperatureSession(private val context:Context,private val scope:CoroutineScope,
    private val log:(String)->Unit={}) : OutsideTemperatureReadSession {
    private var car:Car?=null
    private var generation=0L
    private var work:Job?=null
    private var active=false
    override suspend fun start(observer:OutsideTemperatureReadObserver):AdapterResult<Unit> = withContext(Dispatchers.Main.immediate) {
        if(Build.VERSION.SDK_INT<29) return@withContext AdapterResult.Unavailable(UnavailableReason.UNSUPPORTED)
        if(context.checkSelfPermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT)!=PackageManager.PERMISSION_GRANTED)
            return@withContext AdapterResult.Unavailable(UnavailableReason.PERMISSION_DENIED)
        try {
            active=true
            car=Car.createCar(context.applicationContext,Handler(Looper.getMainLooper()),Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                Car.CarServiceLifecycleListener { instance,ready ->
                    if(!active) return@CarServiceLifecycleListener
                    val token=++generation; val previous=work
                    observer.unavailable(UnavailableReason.DISCONNECTED)
                    work=scope.launch(Dispatchers.Main.immediate) {
                        previous?.cancelAndJoin()
                        if(token!=generation || !ready) return@launch
                        try {
                            val manager=instance.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                            if(manager==null) observer.unavailable(UnavailableReason.UNSUPPORTED)
                            else observe(manager,observer,token)
                        } catch(e:CancellationException) { throw e }
                        catch(e:Exception) { if(token==generation) observer.unavailable(reason(e)) }
                    }
                })
            AdapterResult.Available(Unit)
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { observer.unavailable(reason(e)); AdapterResult.Unavailable(reason(e)) }
    }
    private suspend fun observe(manager:CarPropertyManager,observer:OutsideTemperatureReadObserver,token:Long) {
        val id=VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE
        fun publish(value:CarPropertyValue<*>?,origin:String) {
            if(token!=generation) return
            if(value==null || value.propertyId!=id) { observer.unavailable(UnavailableReason.INVALID_DATA); return }
            observer.sample((value.value as? Float)?.toDouble(),value.timestamp,
                value.status==CarPropertyValue.STATUS_AVAILABLE,value.areaId==0)
            log("outside observation origin=$origin status=${value.status} sampleNanos=${value.timestamp}")
        }
        val events=object:CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value:CarPropertyValue<*>?)=publish(value,"CALLBACK")
            override fun onErrorEvent(propertyId:Int,areaId:Int) {
                if(token==generation && propertyId==id) observer.unavailable(UnavailableReason.SDK_ERROR)
            }
        }
        try {
            val config=withContext(Dispatchers.IO) { manager.getCarPropertyConfig(id) }
            if(token!=generation) return
            if(config==null || config.access !in setOf(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) || !config.areaIds.contains(0)) {
                observer.unavailable(UnavailableReason.UNSUPPORTED); return
            }
            if(config.propertyType!=java.lang.Float::class.java) { observer.unavailable(UnavailableReason.INVALID_DATA); return }
            publish(withContext(Dispatchers.IO) { manager.getProperty<Float>(id,0) },"GET")
            val rate=if(config.changeMode==CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS)
                CarPropertyManager.SENSOR_RATE_NORMAL.coerceIn(config.minSampleRate,config.maxSampleRate)
            else CarPropertyManager.SENSOR_RATE_ONCHANGE
            val registered=withContext(Dispatchers.IO) { manager.registerCallback(events,id,rate) }
            if(token!=generation) return
            if(!registered) observer.unavailable(UnavailableReason.SDK_ERROR)
            else { log("outside registered"); awaitCancellation() }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { if(token==generation) observer.unavailable(reason(e)) }
        finally { withContext(NonCancellable+Dispatchers.IO) {
            try { manager.unregisterCallback(events) } catch(_:Exception) { log("outside unregister unavailable") }
        } }
    }
    override suspend fun close()=withContext(Dispatchers.Main.immediate) {
        active=false; ++generation; work?.cancelAndJoin(); work=null
        try { car?.disconnect() } finally { car=null }
        log("outside session released")
    }
    private fun reason(e:Exception)=if(e is SecurityException) UnavailableReason.PERMISSION_DENIED else UnavailableReason.SDK_ERROR
}
