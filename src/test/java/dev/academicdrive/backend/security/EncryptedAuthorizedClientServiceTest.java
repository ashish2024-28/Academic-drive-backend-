package dev.academicdrive.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.academicdrive.backend.config.AesGcmTokenCipher;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

class EncryptedAuthorizedClientServiceTest {
    private static final String PRINCIPAL = "google-subject-123";
    private static final String DRIVE_REGISTRATION = "google-drive";

    private final StoredAuthorizedClientRepository repository = mock(StoredAuthorizedClientRepository.class);
    private final ClientRegistrationRepository registrations = mock(ClientRegistrationRepository.class);
    private final AesGcmTokenCipher cipher =
            new AesGcmTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final EncryptedAuthorizedClientService service =
            new EncryptedAuthorizedClientService(repository, registrations, cipher);

    @Test
    void persistsDriveTokensEncryptedAndCanRestoreThemForRefresh() {
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "plain-access-token",
                issuedAt,
                issuedAt.plusSeconds(3600),
                Set.of("https://www.googleapis.com/auth/drive.file"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("plain-refresh-token", issuedAt);
        ClientRegistration registration = registration(DRIVE_REGISTRATION);
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                registration, PRINCIPAL, accessToken, refreshToken);
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(PRINCIPAL, null, "ROLE_USER");

        when(repository.findByClientRegistrationIdAndPrincipalName(DRIVE_REGISTRATION, PRINCIPAL))
                .thenReturn(Optional.empty(), Optional.of(savedClient(accessToken, refreshToken)));
        when(registrations.findByRegistrationId(DRIVE_REGISTRATION)).thenReturn(registration);

        service.saveAuthorizedClient(authorizedClient, authentication);

        org.mockito.ArgumentCaptor<StoredAuthorizedClient> stored =
                org.mockito.ArgumentCaptor.forClass(StoredAuthorizedClient.class);
        verify(repository).save(stored.capture());
        assertNotNull(stored.getValue().getAccessTokenCiphertext());
        assertNotNull(stored.getValue().getRefreshTokenCiphertext());
        assertFalse(stored.getValue().getAccessTokenCiphertext().contains("plain-access-token"));
        assertFalse(stored.getValue().getRefreshTokenCiphertext().contains("plain-refresh-token"));
        assertEquals("plain-access-token", cipher.decrypt(stored.getValue().getAccessTokenCiphertext()));
        assertEquals("plain-refresh-token", cipher.decrypt(stored.getValue().getRefreshTokenCiphertext()));

        OAuth2AuthorizedClient restored =
                service.loadAuthorizedClient(DRIVE_REGISTRATION, PRINCIPAL);
        assertEquals("plain-access-token", restored.getAccessToken().getTokenValue());
        assertEquals("plain-refresh-token", restored.getRefreshToken().getTokenValue());
        assertEquals(accessToken.getExpiresAt(), restored.getAccessToken().getExpiresAt());
    }

    @Test
    void doesNotPersistTheProfileOnlyLoginToken() {
        OAuth2AuthorizedClient profileClient = new OAuth2AuthorizedClient(
                registration("google"),
                PRINCIPAL,
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "profile-token",
                        Instant.now(),
                        Instant.now().plusSeconds(3600)),
                null);

        service.saveAuthorizedClient(
                profileClient,
                new TestingAuthenticationToken(PRINCIPAL, null, "ROLE_USER"));

        verify(repository, never()).save(any(StoredAuthorizedClient.class));
    }

    private StoredAuthorizedClient savedClient(
            OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken) {
        StoredAuthorizedClient stored = new StoredAuthorizedClient();
        stored.initialize(DRIVE_REGISTRATION, PRINCIPAL);
        stored.updateAccessToken(
                cipher.encrypt(accessToken.getTokenValue()),
                accessToken.getIssuedAt(),
                accessToken.getExpiresAt(),
                String.join(" ", accessToken.getScopes()));
        stored.updateRefreshToken(
                cipher.encrypt(refreshToken.getTokenValue()), refreshToken.getIssuedAt());
        return stored;
    }

    private static ClientRegistration registration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email", "profile")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .build();
    }
}
