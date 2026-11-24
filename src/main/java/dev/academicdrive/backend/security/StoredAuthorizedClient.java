package dev.academicdrive.backend.security;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("oauth_authorized_clients")
@CompoundIndex(
        name = "idx_oauth_client_registration_principal",
        def = "{'clientRegistrationId': 1, 'principalName': 1}",
        unique = true)
public class StoredAuthorizedClient {
    @Id
    private String id;

    private String clientRegistrationId;
    private String principalName;
    private String accessTokenCiphertext;
    private Instant accessTokenIssuedAt;
    private Instant accessTokenExpiresAt;
    private String accessTokenScopes;
    private String refreshTokenCiphertext;
    private Instant refreshTokenIssuedAt;
    private Instant updatedAt;

    protected StoredAuthorizedClient() {
    }

    public void initialize(String registrationId, String principal) {
        clientRegistrationId = registrationId;
        principalName = principal;
        id = registrationId + ":" + principal;
        updatedAt = Instant.now();
    }

    public void updateAccessToken(
            String ciphertext, Instant issuedAt, Instant expiresAt, String scopes) {
        accessTokenCiphertext = ciphertext;
        accessTokenIssuedAt = issuedAt;
        accessTokenExpiresAt = expiresAt;
        accessTokenScopes = scopes;
        updatedAt = Instant.now();
    }

    public void updateRefreshToken(String ciphertext, Instant issuedAt) {
        refreshTokenCiphertext = ciphertext;
        refreshTokenIssuedAt = issuedAt;
        updatedAt = Instant.now();
    }

    public String getClientRegistrationId() {
        return clientRegistrationId;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public String getAccessTokenCiphertext() {
        return accessTokenCiphertext;
    }

    public Instant getAccessTokenIssuedAt() {
        return accessTokenIssuedAt;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public String getAccessTokenScopes() {
        return accessTokenScopes;
    }

    public String getRefreshTokenCiphertext() {
        return refreshTokenCiphertext;
    }

    public Instant getRefreshTokenIssuedAt() {
        return refreshTokenIssuedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
