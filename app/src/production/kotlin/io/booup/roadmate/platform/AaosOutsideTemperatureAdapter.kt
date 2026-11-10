package io.booup.roadmate.platform

import io.booup.roadmate.contracts.OutsideTemperatureAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Testable transport seam. Units/status/timestamp are normalized, not Android class types. */
interface OutsideTemperatureReadObserver {
    fun sample(celsius: Double?, timestampNanos: Long, available: Boolean, globalArea: Boolean)
    fun unavailable(reason: UnavailableReason)
}
interface OutsideTemperatureReadSession {
    suspend fun start(observer: OutsideTemperatureReadObserver): AdapterResult<Unit>
    suspend fun close()
}

/** Only samples from the current session can publish; a cached timestamp is never refreshed. */
class AaosOutsideTemperatureAdapter(private val clock: ContextClock,
    private val sessionFactory: () -> OutsideTemperatureReadSession) : OutsideTemperatureAdapter {
    private val mutable=MutableStateFlow<Reading<Double>>(Reading.Unavailable(UnavailableReason.NOT_CONNECTED,DataSource.SYSTEM))
    override val readings=mutable.asStateFlow()
    private val mutex=Mutex()
    private val lock=Any()
    private var generation=0L
    private var session:OutsideTemperatureReadSession?=null
    override suspend fun connect():AdapterResult<Unit> = mutex.withLock {
        if(session!=null) return@withLock AdapterResult.Available(Unit)
        val token=synchronized(lock) { ++generation }
        try {
            val next=sessionFactory(); synchronized(lock) { session=next }
            next.start(object:OutsideTemperatureReadObserver {
                override fun sample(celsius:Double?,timestampNanos:Long,available:Boolean,globalArea:Boolean) = receive(token) {
                    val now=clock.now(); val sampleMillis=timestampNanos/1_000_000; val age=now.monotonicMillis-sampleMillis
                    val candidate=when {
                        !available -> Reading.Unavailable(UnavailableReason.NO_VALUE,DataSource.SYSTEM)
                        !globalArea || celsius==null || !celsius.isFinite() || celsius !in -100.0..100.0 ||
                            timestampNanos<=0 || age<0 || now.epochMillis<age -> Reading.Unavailable(UnavailableReason.INVALID_DATA,DataSource.SYSTEM)
                        else -> Reading.Observed(celsius,DataMetadata(DataSource.SYSTEM,now.epochMillis-age,Freshness.FRESH,
                            CapabilityState.SUPPORTED,PermissionState.GRANTED,DataQuality.VALID,sampleMillis,
                            now.monotonicMillis,timestampNanos))
                    }
                    if(!candidate.isOlderObservationThan(mutable.value)) mutable.value=candidate
                }
                override fun unavailable(reason:UnavailableReason)=receive(token) { mutable.value=Reading.Unavailable(reason,DataSource.SYSTEM) }
            }).also { if(it is AdapterResult.Unavailable) receive(token) { mutable.value=Reading.Unavailable(it.reason,DataSource.SYSTEM) } }
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(_:Exception) { mutable.value=Reading.Unavailable(UnavailableReason.SDK_ERROR,DataSource.SYSTEM); AdapterResult.Unavailable(UnavailableReason.SDK_ERROR) }
    }
    override suspend fun disconnect()=mutex.withLock {
        val old=synchronized(lock) { ++generation; val previous=session; session=null
            mutable.value=Reading.Unavailable(UnavailableReason.DISCONNECTED,DataSource.SYSTEM); previous }
        old?.close()
        Unit
    }
    private fun receive(token:Long,block:()->Unit)=synchronized(lock) { if(token==generation && session!=null) block() }
}
