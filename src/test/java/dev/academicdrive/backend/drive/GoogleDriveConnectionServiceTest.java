package dev.academicdrive.backend.drive;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.academicdrive.backend.auth.GoogleUserDocument;
import dev.academicdrive.backend.auth.GoogleUserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleDriveConnectionServiceTest {
    @Test
    void synchronizesCollegeNameInDriveFolderDescriptionWithoutDiscardingExistingText() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClientService authorizedClients = mock(OAuth2AuthorizedClientService.class);
        GoogleUserRepository users = mock(GoogleUserRepository.class);
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleDriveConnectionService service =
                new GoogleDriveConnectionService(
                        manager,
                        authorizedClients,
                        users,
                        restClientBuilder,
                        "https://www.googleapis.com",
                        "https://www.googleapis.com/oauth2/v3/userinfo",
                        "https://oauth2.googleapis.com/revoke");

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "server-only-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        ClientRegistration registration = ClientRegistration.withRegistrationId("google-drive")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email", "profile", "https://www.googleapis.com/auth/drive.file")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .build();
        when(manager.authorize(any())).thenReturn(
                new OAuth2AuthorizedClient(registration, "google-subject", accessToken, null));
        server.expect(requestTo("https://www.googleapis.com/oauth2/v3/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"email\":\"student@example.com\",\"email_verified\":true}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals(
                        "/drive/v3/files/folder-123", request.getURI().getPath()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"folder-123\",\"name\":\"academic drive\","
                                + "\"description\":\"Personal notes\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals(
                        "/drive/v3/files/folder-123", request.getURI().getPath()))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json(
                        "{\"description\":\"Personal notes\\n"
                                + "Academic Drive - College / University: North Valley University\"}"))
                .andRespond(withSuccess());

        GoogleUserDocument account =
                new GoogleUserDocument("google-subject", "student@example.com");
        account.restoreDriveConnection(
                "folder-123", "academic drive", Instant.now(), Instant.now());
        service.synchronizeCollegeDescription(
                new TestingAuthenticationToken("google-subject", null, "ROLE_USER"),
                account,
                "North Valley University");

        server.verify();
    }

    @Test
    void removesOnlyTheManagedDriveDescriptionWhenCollegeNameIsCleared() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClientService authorizedClients = mock(OAuth2AuthorizedClientService.class);
        GoogleUserRepository users = mock(GoogleUserRepository.class);
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleDriveConnectionService service =
                new GoogleDriveConnectionService(
                        manager,
                        authorizedClients,
                        users,
                        restClientBuilder,
                        "https://www.googleapis.com",
                        "https://www.googleapis.com/oauth2/v3/userinfo",
                        "https://oauth2.googleapis.com/revoke");

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "server-only-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        ClientRegistration registration = ClientRegistration.withRegistrationId("google-drive")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email", "profile", "https://www.googleapis.com/auth/drive.file")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .build();
        when(manager.authorize(any())).thenReturn(
                new OAuth2AuthorizedClient(registration, "google-subject", accessToken, null));
        server.expect(requestTo("https://www.googleapis.com/oauth2/v3/userinfo"))
                .andRespond(withSuccess(
                        "{\"email\":\"student@example.com\",\"email_verified\":true}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals(
                        "/drive/v3/files/folder-123", request.getURI().getPath()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"folder-123\",\"name\":\"academic drive\","
                                + "\"description\":\"Academic Drive - College / University: "
                                + "North Valley University\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals(
                        "/drive/v3/files/folder-123", request.getURI().getPath()))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"description\":\"\"}"))
                .andRespond(withSuccess());

        GoogleUserDocument account =
                new GoogleUserDocument("google-subject", "student@example.com");
        account.restoreDriveConnection(
                "folder-123", "academic drive", Instant.now(), Instant.now());
        service.synchronizeCollegeDescription(
                new TestingAuthenticationToken("google-subject", null, "ROLE_USER"),
                account,
                null);

        server.verify();
    }

    @Test
    void verifiesDriveAccountCreatesRootAndPersistsConnectionWithoutReturningTokens() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClientService authorizedClients = mock(OAuth2AuthorizedClientService.class);
        GoogleUserRepository users = mock(GoogleUserRepository.class);
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleDriveConnectionService service =
                new GoogleDriveConnectionService(
                        manager,
                        authorizedClients,
                        users,
                        restClientBuilder,
                        "https://www.googleapis.com",
                        "https://www.googleapis.com/oauth2/v3/userinfo",
                        "https://oauth2.googleapis.com/revoke");

        Instant expiresAt = Instant.now().plusSeconds(3600);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "server-only-access-token",
                Instant.now(),
                expiresAt);
        ClientRegistration registration = ClientRegistration.withRegistrationId("google-drive")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email", "profile", "https://www.googleapis.com/auth/drive.file")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .build();
        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
                registration, "google-subject", accessToken, null);
        when(manager.authorize(any())).thenReturn(client);

        server.expect(requestTo("https://www.googleapis.com/oauth2/v3/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"email\":\"student@example.com\",\"email_verified\":true}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals("/drive/v3/files", request.getURI().getPath()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.googleapis.com/drive/v3/files?supportsAllDrives=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"id\":\"drive-root-123\",\"name\":\"academic drive\",\"mimeType\":\"application/vnd.google-apps.folder\"}",
                        MediaType.APPLICATION_JSON));

        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn("google-subject");
        when(user.getEmail()).thenReturn("student@example.com");
        GoogleUserDocument profile =
                new GoogleUserDocument("google-subject", "student@example.com");
        profile.recordSuccessfulLogin("student@example.com", Instant.now());
        when(users.findById("google-subject")).thenReturn(Optional.of(profile));

        DriveStatusResponse status = service.getStatus(
                new TestingAuthenticationToken("google-subject", null, "ROLE_USER"),
                user);

        server.verify();
        assertTrue(status.connected());
        assertEquals("student@example.com", status.email());
        assertEquals("drive-root-123", status.rootFolderId());
        assertEquals(expiresAt, status.accessTokenExpiresAt());
        verify(users).save(profile);
        assertNotNull(profile.getDriveConnection());
        assertEquals("drive-root-123", profile.getDriveConnection().rootFolderId());
        assertEquals("academic drive", profile.getDriveConnection().rootFolderName());
    }
}
