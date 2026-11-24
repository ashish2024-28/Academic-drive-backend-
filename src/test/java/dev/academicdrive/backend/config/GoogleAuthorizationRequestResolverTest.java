package dev.academicdrive.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class GoogleAuthorizationRequestResolverTest {
    private final ClientRegistrationRepository registrations =
            Mockito.mock(ClientRegistrationRepository.class);
    private final GoogleAuthorizationRequestResolver resolver =
            new GoogleAuthorizationRequestResolver(registrations);

    @Test
    void requestsOfflineAccessOnlyForTheSeparateDriveConnectionFlow() {
        ClientRegistration profileRegistration = registration("google");
        ClientRegistration driveRegistration = registration("google-drive");
        when(registrations.findByRegistrationId("google")).thenReturn(profileRegistration);
        when(registrations.findByRegistrationId("google-drive")).thenReturn(driveRegistration);

        var profileRequest = resolver.resolve(request("/oauth2/authorization/google"));
        var driveRequest = resolver.resolve(request("/oauth2/authorization/google-drive"));

        assertFalse(profileRequest.getAdditionalParameters().containsKey("access_type"));
        assertEquals("offline", driveRequest.getAdditionalParameters().get("access_type"));
        assertEquals("consent", driveRequest.getAdditionalParameters().get("prompt"));
    }

    private static HttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
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
