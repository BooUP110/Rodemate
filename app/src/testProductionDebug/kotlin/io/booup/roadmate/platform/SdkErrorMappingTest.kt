package io.booup.roadmate.platform

import ai.pleos.playground.vehicle.constant.ErrorCode
import io.booup.roadmate.model.UnavailableReason
import org.junit.Assert.*
import org.junit.Test

class SdkErrorMappingTest {
    @Test fun exactArtifactErrorCodesPreserveFailureCategories() {
        val expected=mapOf(
            ErrorCode.ERROR_SECURITY_PERMISSION to UnavailableReason.PERMISSION_DENIED,
            ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY to UnavailableReason.UNSUPPORTED,
            ErrorCode.ERROR_NOT_SUPPORTED_AREA to UnavailableReason.UNSUPPORTED,
            ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE to UnavailableReason.DISCONNECTED,
            ErrorCode.ERROR_NOT_SUPPORTED_VALUE_TYPE to UnavailableReason.INVALID_DATA,
            ErrorCode.ERROR_EMPTY_RESULT to UnavailableReason.NO_VALUE,
            ErrorCode.ERROR_INTERNAL_SERVICE to UnavailableReason.SDK_ERROR,
            ErrorCode.ERROR_DRIVING_RESTRICTED to UnavailableReason.SDK_ERROR,
            ErrorCode.ERROR_UNDEFINED to UnavailableReason.SDK_ERROR)
        assertEquals(ErrorCode.entries.toSet(),expected.keys)
        expected.forEach { (code,reason) -> assertEquals(code.name,reason,SdkErrorMapping.reason(code)) }
    }
    @Test fun arbitraryExceptionMessagesCannotForgePermissionOrCapability() {
        assertEquals(UnavailableReason.SDK_ERROR,SdkErrorMapping.reason(Exception("ERROR_SECURITY_PERMISSION")))
        assertEquals(UnavailableReason.PERMISSION_DENIED,SdkErrorMapping.reason(SecurityException()))
        assertNull(SdkErrorMapping.code(Exception()))
    }
}
