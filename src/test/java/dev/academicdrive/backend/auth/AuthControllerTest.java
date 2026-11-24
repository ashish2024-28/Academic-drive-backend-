package dev.academicdrive.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.academicdrive.backend.drive.GoogleDriveConnectionService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class AuthControllerTest {
    @Test
    void savesCollegeNameAndReturnsPersistentProfileDetails() {
        GoogleUserRepository users = mock(GoogleUserRepository.class);
        GoogleDriveConnectionService driveConnections = mock(GoogleDriveConnectionService.class);
        GoogleUserDocument account = new GoogleUserDocument("google-subject", "student@example.com");
        Instant loginAt = Instant.parse("2026-11-23T10:00:00Z");
        account.recordSuccessfulLogin("student@example.com", loginAt);
        when(users.findById("google-subject")).thenReturn(Optional.of(account));
        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn("google-subject");
        when(user.getEmail()).thenReturn("student@example.com");
        when(user.getFullName()).thenReturn("Student Example");
        when(user.getPicture()).thenReturn("https://example.com/avatar.png");

        AuthController controller = new AuthController(users, driveConnections);
        GoogleUserProfile updated = controller.updateProfile(
                new TestingAuthenticationToken("google-subject", null, "ROLE_USER"),
                user,
                new GoogleUserProfileUpdateRequest("  North Valley University  "));

        assertEquals("North Valley University", account.getCollegeOrUniversity());
        assertEquals(loginAt, updated.createdAt());
        assertEquals(loginAt, updated.lastLoginAt());
        assertEquals("North Valley University", updated.collegeOrUniversity());
        verify(driveConnections).synchronizeCollegeDescription(
                any(), org.mockito.ArgumentMatchers.eq(account), org.mockito.ArgumentMatchers.eq(
                        "North Valley University"));
        verify(users).save(account);
    }
}
