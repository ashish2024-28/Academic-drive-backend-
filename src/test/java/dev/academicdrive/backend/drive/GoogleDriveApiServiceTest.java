package dev.academicdrive.backend.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class GoogleDriveApiServiceTest {
    @Test
    void fetchesDriveStorageQuotaUsingTheServerAuthorizedClient() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleDriveApiService service =
                new GoogleDriveApiService(manager, builder, "https://www.googleapis.com");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "server-only-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        ClientRegistration registration = ClientRegistration.withRegistrationId("google-drive")
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email", "profile", "https://www.googleapis.com/auth/drive.file")
                .build();
        when(manager.authorize(any())).thenReturn(
                new OAuth2AuthorizedClient(registration, "google-subject", accessToken));

        server.expect(requestTo("https://www.googleapis.com/drive/v3/about?fields=storageQuota(limit,usage,usageInDrive,usageInDriveTrash)"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer server-only-token"))
                .andRespond(withSuccess("{\"storageQuota\":{\"usage\":\"100\",\"limit\":\"1000\"}}", MediaType.APPLICATION_JSON));

        DriveApiResponse response = service.getAbout(
                new TestingAuthenticationToken("google-subject", null, "ROLE_USER"));

        server.verify();
        assertEquals(200, response.status());
        assertEquals("{\"storageQuota\":{\"usage\":\"100\",\"limit\":\"1000\"}}", new String(response.body()));
    }

    @Test
    void proxiesDriveRequestsUsingTheServerAuthorizedClient() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleDriveApiService service =
                new GoogleDriveApiService(manager, builder, "https://www.googleapis.com");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "server-only-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        ClientRegistration registration = ClientRegistration.withRegistrationId("google-drive")
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email", "profile", "https://www.googleapis.com/auth/drive.file")
                .build();
        when(manager.authorize(any())).thenReturn(
                new OAuth2AuthorizedClient(registration, "google-subject", accessToken));

        server.expect(requestTo("https://www.googleapis.com/drive/v3/files?q=trashed%20%3D%20false"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer server-only-token"))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("q", "trashed = false");

        DriveApiResponse response = service.listFiles(
                new TestingAuthenticationToken("google-subject", null, "ROLE_USER"),
                query);

        server.verify();
        assertEquals(200, response.status());
        assertEquals("{\"files\":[]}", new String(response.body()));
    }

    @Test
    void rejectsUnsupportedQueryParametersInsteadOfForwardingThem() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        GoogleDriveApiService service = new GoogleDriveApiService(
                manager, RestClient.builder(), "https://www.googleapis.com");
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("access_token", "must-not-be-forwarded");

        assertThrows(ResponseStatusException.class, () -> service.listFiles(
                new TestingAuthenticationToken("google-subject", null, "ROLE_USER"),
                query));
    }
}
