package dev.academicdrive.backend.security;

import dev.academicdrive.backend.config.AesGcmTokenCipher;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;

@Service
public class EncryptedAuthorizedClientService implements OAuth2AuthorizedClientService {
    public static final String GOOGLE_DRIVE_REGISTRATION_ID = "google-drive";

    private final StoredAuthorizedClientRepository authorizedClients;
    private final ClientRegistrationRepository registrations;
    private final AesGcmTokenCipher cipher;

    public EncryptedAuthorizedClientService(
            StoredAuthorizedClientRepository authorizedClients,
            ClientRegistrationRepository registrations,
            AesGcmTokenCipher cipher) {
        this.authorizedClients = authorizedClients;
        this.registrations = registrations;
        this.cipher = cipher;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, String principalName) {
        if (!GOOGLE_DRIVE_REGISTRATION_ID.equals(clientRegistrationId)) {
            return null;
        }
        return authorizedClients
                .findByClientRegistrationIdAndPrincipalName(clientRegistrationId, principalName)
                .map(entity -> rehydrate(entity, clientRegistrationId, principalName))
                .map(client -> (T) client)
                .orElse(null);
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal) {
        if (!GOOGLE_DRIVE_REGISTRATION_ID.equals(client.getClientRegistration().getRegistrationId())) {
            return;
        }
        String principalName = principal.getName();
        StoredAuthorizedClient entity = authorizedClients
                .findByClientRegistrationIdAndPrincipalName(
                        client.getClientRegistration().getRegistrationId(), principalName)
                .orElseGet(() -> {
                    StoredAuthorizedClient created = new StoredAuthorizedClient();
                    created.initialize(client.getClientRegistration().getRegistrationId(), principalName);
                    return created;
                });

        OAuth2AccessToken accessToken = client.getAccessToken();
        entity.updateAccessToken(
                cipher.encrypt(accessToken.getTokenValue()),
                accessToken.getIssuedAt(),
                accessToken.getExpiresAt(),
                accessToken.getScopes().stream().sorted().collect(Collectors.joining(" ")));
        OAuth2RefreshToken refreshToken = client.getRefreshToken();
        if (refreshToken != null) {
            entity.updateRefreshToken(
                    cipher.encrypt(refreshToken.getTokenValue()), refreshToken.getIssuedAt());
        }
        authorizedClients.save(entity);
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        if (!GOOGLE_DRIVE_REGISTRATION_ID.equals(clientRegistrationId)) {
            return;
        }
        authorizedClients.deleteByClientRegistrationIdAndPrincipalName(
                clientRegistrationId, principalName);
    }

    private OAuth2AuthorizedClient rehydrate(
            StoredAuthorizedClient entity, String clientRegistrationId, String principalName) {
        ClientRegistration registration = registrations.findByRegistrationId(clientRegistrationId);
        if (registration == null) {
            throw new IllegalStateException("OAuth client registration is not configured.");
        }

        Set<String> scopes = entity.getAccessTokenScopes().isBlank()
                ? Set.of()
                : Arrays.stream(entity.getAccessTokenScopes().split(" "))
                        .filter(scope -> !scope.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                cipher.decrypt(entity.getAccessTokenCiphertext()),
                entity.getAccessTokenIssuedAt(),
                entity.getAccessTokenExpiresAt(),
                scopes);
        String encryptedRefreshToken = entity.getRefreshTokenCiphertext();
        OAuth2RefreshToken refreshToken = encryptedRefreshToken == null
                ? null
                : new OAuth2RefreshToken(
                        cipher.decrypt(encryptedRefreshToken), entity.getRefreshTokenIssuedAt());
        return new OAuth2AuthorizedClient(
                registration, principalName, accessToken, refreshToken);
    }
}
