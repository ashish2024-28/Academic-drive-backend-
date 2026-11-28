package dev.academicdrive.backend.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

class DriveSyncLockControllerTest {
    @Test
    void returnsAcceptedWhenAnotherSyncOwnsTheLease() {
        DriveSyncLockService lockService = mock(DriveSyncLockService.class);
        when(lockService.tryAcquire("google-subject")).thenReturn(Optional.empty());
        DriveController controller = new DriveController(
                mock(GoogleDriveConnectionService.class),
                mock(GoogleDriveApiService.class),
                lockService);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("google-subject");

        var response = controller.acquireSyncLock(authentication);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("1", response.getHeaders().getFirst("Retry-After"));
        assertNotNull(response.getBody());
        assertFalse(response.getBody().acquired());
    }

    @Test
    void returnsTheLeaseTokenWhenAcquisitionSucceeds() {
        DriveSyncLockService lockService = mock(DriveSyncLockService.class);
        when(lockService.tryAcquire("google-subject")).thenReturn(Optional.of("lease-token"));
        DriveController controller = new DriveController(
                mock(GoogleDriveConnectionService.class),
                mock(GoogleDriveApiService.class),
                lockService);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("google-subject");

        var response = controller.acquireSyncLock(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().acquired());
        assertEquals("lease-token", response.getBody().token());
    }
}
