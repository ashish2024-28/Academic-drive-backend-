package dev.academicdrive.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class GoogleProfileOidcUserServiceTest {
    private final GoogleUserRepository users = mock(GoogleUserRepository.class);
    private final OidcUserService delegate = mock(OidcUserService.class);
    private final GoogleProfileOidcUserService service =
            new GoogleProfileOidcUserService(users, delegate);
    private final OidcUserRequest request = mock(OidcUserRequest.class);
    private final ClientRegistration registration = mock(ClientRegistration.class);
    private final OidcUser oidcUser = mock(OidcUser.class);

    GoogleProfileOidcUserServiceTest() {
        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("google");
    }

    @Test
    void storesOnlyTheVerifiedBasicGoogleProfile() {
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("student@example.com");
        when(oidcUser.getClaim("email_verified")).thenReturn(true);
        when(users.findById("google-subject")).thenReturn(Optional.empty());

        service.loadUser(request);

        ArgumentCaptor<GoogleUserDocument> stored = ArgumentCaptor.forClass(GoogleUserDocument.class);
        verify(users).save(stored.capture());
        assertEquals("google-subject", stored.getValue().getGoogleSubject());
        assertEquals("student@example.com", stored.getValue().getEmail());
        assertNull(stored.getValue().getDriveConnection());
        assertNotNull(stored.getValue().getCreatedAt());
        assertNotNull(stored.getValue().getLastLoginAt());
    }

    @Test
    void updatesLastLoginWithoutChangingCreatedAtOrEmbeddedDriveConnection() {
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
        GoogleUserDocument existing =
                new GoogleUserDocument("google-subject", "student@example.com");
        existing.recordSuccessfulLogin("student@example.com", createdAt);
        existing.restoreDriveConnection(
                "folder-id", "academic drive", createdAt, createdAt);
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("student@example.com");
        when(oidcUser.getClaim("email_verified")).thenReturn(true);
        when(users.findById("google-subject")).thenReturn(Optional.of(existing));

        Instant beforeLogin = Instant.now();
        service.loadUser(request);
        Instant afterLogin = Instant.now();

        assertEquals(createdAt, existing.getCreatedAt());
        assertTrue(!existing.getLastLoginAt().isBefore(beforeLogin));
        assertTrue(!existing.getLastLoginAt().isAfter(afterLogin));
        assertEquals("folder-id", existing.getDriveConnection().rootFolderId());
        verify(users).save(existing);
    }

    @Test
    void refusesToPersistAnUnverifiedGoogleEmail() {
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("student@example.com");
        when(oidcUser.getClaim("email_verified")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.loadUser(request));
        verify(users, never()).save(any(GoogleUserDocument.class));
    }

    @Test
    void doesNotCountDriveConsentAsAnotherProfileLogin() {
        when(registration.getRegistrationId()).thenReturn("google-drive");
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("student@example.com");
        when(oidcUser.getClaim("email_verified")).thenReturn(true);

        service.loadUser(request);

        verify(users, never()).save(any(GoogleUserDocument.class));
    }
}
