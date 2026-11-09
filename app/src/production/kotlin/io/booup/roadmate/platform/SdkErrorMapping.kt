package io.booup.roadmate.platform

import ai.pleos.playground.vehicle.callback.exception.a as VehicleException232
import ai.pleos.playground.vehicle.constant.ErrorCode
import io.booup.roadmate.model.UnavailableReason

/** Version-bound public bytecode API: 2.3.2 R8 renamed VehicleException/getErrorCode to a/a().
 * Verified by javap including VehicleException.kt SMAP; no reflection or message parsing.
 * Revalidate this boundary before changing the pinned artifact.
 */
object SdkErrorMapping {
    fun code(exception: Exception): ErrorCode? = (exception as? VehicleException232)?.a()
    fun reason(exception: Exception): UnavailableReason = if (exception is SecurityException)
        UnavailableReason.PERMISSION_DENIED else reason(code(exception))
    fun reason(code: ErrorCode?): UnavailableReason = when (code) {
        ErrorCode.ERROR_SECURITY_PERMISSION -> UnavailableReason.PERMISSION_DENIED
        ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY, ErrorCode.ERROR_NOT_SUPPORTED_AREA -> UnavailableReason.UNSUPPORTED
        ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE -> UnavailableReason.DISCONNECTED
        ErrorCode.ERROR_NOT_SUPPORTED_VALUE_TYPE -> UnavailableReason.INVALID_DATA
        ErrorCode.ERROR_EMPTY_RESULT -> UnavailableReason.NO_VALUE
        else -> UnavailableReason.SDK_ERROR
    }
}
