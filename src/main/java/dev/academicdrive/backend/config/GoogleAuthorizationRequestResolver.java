package dev.academicdrive.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class GoogleAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public GoogleAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        delegate = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, "/oauth2/authorization");
        delegate.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean driveAuthorization = path.endsWith("/google-drive");
        return addOfflineAccess(delegate.resolve(request), driveAuthorization);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return addOfflineAccess(
                delegate.resolve(request, clientRegistrationId),
                "google-drive".equals(clientRegistrationId));
    }

    private OAuth2AuthorizationRequest addOfflineAccess(
            OAuth2AuthorizationRequest authorizationRequest, boolean driveAuthorization) {
        if (authorizationRequest == null || !driveAuthorization) {
            return authorizationRequest;
        }
        Map<String, Object> parameters = new HashMap<>(authorizationRequest.getAdditionalParameters());
        parameters.put("access_type", "offline");
        parameters.put("prompt", "consent");
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(parameters)
                .build();
    }
}
