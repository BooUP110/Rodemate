package io.booup.roadmate.platform

import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.contracts.DrivingRestrictionAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrivingRestrictionBridgeTest {
    private class Restrictions : DrivingRestrictionAdapter {
        override val restrictions=MutableStateFlow(DrivingRestrictions())
        override suspend fun connect()=AdapterResult.Available(Unit)
        override suspend fun disconnect() { restrictions.value=DrivingRestrictions() }
    }
    private class Session : VehicleReadSession {
        override fun start(observer:VehicleReadObserver) { observer.capability("lowBeam",true,"READ") }
        override fun refresh() {}
        override fun close() {}
    }
    @Test fun missingOrInconsistentPlatformFieldsFailClosed() {
        assertEquals(UIMode.UNKNOWN_RESTRICTED,normalizeRestriction(null,0,1000).mode)
        assertEquals(UIMode.UNKNOWN_RESTRICTED,normalizeRestriction(false,null,1000).mode)
        assertEquals(UIMode.RESTRICTED,normalizeRestriction(false,80,1000).mode)
        assertEquals(UIMode.RESTRICTED,normalizeRestriction(true,0,1000).mode)
        assertEquals(UIMode.PARKED,normalizeRestriction(false,0,1000).mode)
    }
    @Test fun actualPlatformPortTransitionsReachGateWithoutInventingPhysicalParking() = runTest {
        var now=1000L
        val clock=ContextClock { ObservationTime(now,now) }
        val restrictions=Restrictions()
        val adapter=PleosVehicleAdapter(clock,backgroundScope,{Session()},restrictionAdapter=restrictions)
        val store=VehicleContextStore(adapter,RuntimeMode.PRODUCTION,clock,backgroundScope)
        val gate=ContentGate()
        store.foreground(); runCurrent()
        assertFalse(gate.access(ContentArea.SEASON_PASS,store.context.value).allowed)
        restrictions.restrictions.value=normalizeRestriction(true,80,now); runCurrent()
        assertFalse(gate.access(ContentArea.COLLECTION,store.context.value).allowed)
        assertTrue(gate.access(ContentArea.CARE_STATUS,store.context.value).allowed)
        now=2000
        restrictions.restrictions.value=normalizeRestriction(false,0,now); runCurrent()
        assertTrue(gate.access(ContentArea.SEASON_PASS,store.context.value).allowed)
        assertEquals(DataSource.SYSTEM,store.context.value.restrictions.source)
        assertNull(store.context.value.motion.currentValue)
        restrictions.restrictions.value=DrivingRestrictions(); runCurrent()
        assertFalse(gate.access(ContentArea.DETAILED_REFLECTION,store.context.value).allowed)
        store.background(); runCurrent()
        restrictions.restrictions.value=normalizeRestriction(false,0,now); runCurrent()
        assertFalse(gate.access(ContentArea.SEASON_PASS,store.context.value).allowed)
        store.close(); runCurrent()
    }
}
