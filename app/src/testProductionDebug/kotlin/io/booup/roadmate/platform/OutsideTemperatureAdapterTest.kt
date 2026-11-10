package io.booup.roadmate.platform

import io.booup.roadmate.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OutsideTemperatureAdapterTest {
    private var now=ObservationTime(1_000_000,1000)
    private val clock=ContextClock { now }
    private class Session(val result:AdapterResult<Unit> = AdapterResult.Available(Unit)):OutsideTemperatureReadSession {
        lateinit var observer:OutsideTemperatureReadObserver
        var closed=false
        override suspend fun start(observer:OutsideTemperatureReadObserver):AdapterResult<Unit> { this.observer=observer; return result }
        override suspend fun close() { closed=true }
        fun value(value:Double=25.0,timestamp:Long=1_000_000_000)=observer.sample(value,timestamp,true,true)
    }
    private fun current(a:AaosOutsideTemperatureAdapter)=a.readings.value.evaluate(now,ContextFreshnessPolicies().external,ConnectionState(ConnectionStatus.CONNECTED),true)
    @Test fun OT01_supportedFreshUsesSystemSourceAndPreservesSampleTimestamp()=runTest {
        val s=Session(); val a=AaosOutsideTemperatureAdapter(clock) { s }; a.connect(); s.value()
        assertEquals(25.0,current(a).currentValue!!,0.0); assertEquals(DataSource.SYSTEM,current(a).source)
        val meta=(a.readings.value as Reading.Observed).metadata
        assertEquals(1_000_000_000L,meta.sourceTimestampNanos); assertEquals(1000L,meta.receivedAtMonotonicMillis)
    }
    @Test fun OT02_unsupportedDoesNotPublishZero()=runTest {
        val s=Session(); val a=AaosOutsideTemperatureAdapter(clock) { s }; a.connect(); s.observer.unavailable(UnavailableReason.UNSUPPORTED)
        assertEquals(Availability.UNSUPPORTED,current(a).availability); assertNull(current(a).currentValue)
    }
    @Test fun OT03_permissionDeniedIsDistinct()=runTest {
        val a=AaosOutsideTemperatureAdapter(clock) { Session(AdapterResult.Unavailable(UnavailableReason.PERMISSION_DENIED)) }
        a.connect(); assertEquals(Availability.PERMISSION_DENIED,current(a).availability)
    }
    @Test fun OT04_disconnectedClearsCurrentAndClosesSession()=runTest {
        val s=Session(); val a=AaosOutsideTemperatureAdapter(clock) { s }; a.connect(); s.value(); a.disconnect()
        assertTrue(s.closed); assertEquals(Availability.DISCONNECTED,current(a).availability); assertNull(current(a).currentValue)
    }
    @Test fun OT05_oldCallbackCannotRetimestampStaleSensorValue()=runTest {
        val s=Session(); val a=AaosOutsideTemperatureAdapter(clock) { s }; a.connect(); s.value()
        now=ObservationTime(1_060_001,61_001); s.value()
        assertEquals(Freshness.STALE,current(a).freshness); assertNull(current(a).currentValue)
        assertEquals(1000L,(a.readings.value as Reading.Observed).metadata.observedAtMonotonicMillis)
    }
    @Test fun OT06_callbackUpdatePublishesNewSensorValue()=runTest {
        val s=Session(); val a=AaosOutsideTemperatureAdapter(clock) { s }; a.connect(); s.value()
        now=ObservationTime(1_001_000,2000); s.value(26.0,2_000_000_000)
        assertEquals(26.0,current(a).currentValue!!,0.0); assertEquals(0L,current(a).ageMillis)
    }
    @Test fun OT07_reconnectUsesNewSessionAndObservation()=runTest {
        val sessions=mutableListOf<Session>(); val a=AaosOutsideTemperatureAdapter(clock) { Session().also { sessions+=it } }
        a.connect(); sessions[0].value(); a.disconnect(); a.connect(); sessions[1].value(27.0)
        assertEquals(2,sessions.size); assertTrue(sessions[0].closed); assertEquals(27.0,current(a).currentValue!!,0.0)
    }
    @Test fun OT08_lateCallbacksAndErrorsFromOldGenerationAreRejected()=runTest {
        val sessions=mutableListOf<Session>(); val a=AaosOutsideTemperatureAdapter(clock) { Session().also { sessions+=it } }
        a.connect(); a.disconnect(); a.connect(); sessions[1].value(28.0)
        sessions[0].value(-5.0); sessions[0].observer.unavailable(UnavailableReason.SDK_ERROR)
        assertEquals(28.0,current(a).currentValue!!,0.0)
    }
    @Test fun invalidUnavailableAndSdkErrorRemainDistinct()=runTest {
        val s=Session(); val a=AaosOutsideTemperatureAdapter(clock) { s }; a.connect(); s.value(Double.NaN)
        assertEquals(UnavailableReason.INVALID_DATA,(a.readings.value as Reading.Unavailable).reason)
        s.observer.sample(25.0,1_000_000_000,false,true)
        assertEquals(UnavailableReason.NO_VALUE,(a.readings.value as Reading.Unavailable).reason)
        s.observer.unavailable(UnavailableReason.SDK_ERROR)
        assertEquals(UnavailableReason.SDK_ERROR,(a.readings.value as Reading.Unavailable).reason)
    }
    @Test fun olderGetCannotOverwriteNewerCallbackInTheSameSession()=runTest {
        val s=Session(); val a=AaosOutsideTemperatureAdapter(clock) { s }; a.connect()
        now=ObservationTime(1_001_000,2000); s.value(27.0,2_000_000_000); s.value(25.0,1_000_000_000)
        assertEquals(27.0,current(a).currentValue!!,0.0)
        assertEquals(2000L,(a.readings.value as Reading.Observed).metadata.observedAtMonotonicMillis)
    }
}
