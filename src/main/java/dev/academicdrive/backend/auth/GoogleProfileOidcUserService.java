package dev.academicdrive.backend.auth;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleProfileOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private static final String GOOGLE_SIGN_IN_REGISTRATION_ID = "google";

    private final GoogleUserRepository users;
    private final OidcUserService delegate;

    @Autowired
    public GoogleProfileOidcUserService(GoogleUserRepository users) {
        this(users, new OidcUserService());
    }

    GoogleProfileOidcUserService(GoogleUserRepository users, OidcUserService delegate) {
        this.users = users;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()
                || !Boolean.TRUE.equals(oidcUser.getClaim("email_verified"))) {
            throw new IllegalStateException("Google did not provide a verified email address.");
        }
        if (!GOOGLE_SIGN_IN_REGISTRATION_ID.equals(
                userRequest.getClientRegistration().getRegistrationId())) {
            return oidcUser;
        }

        Instant loginAt = Instant.now();
        GoogleUserDocument user = users.findById(oidcUser.getSubject())
                .map(existing -> {
                    existing.recordSuccessfulLogin(email, loginAt);
                    return existing;
                })
                .orElseGet(() -> {
                    GoogleUserDocument created = new GoogleUserDocument(
                            oidcUser.getSubject(), email);
                    created.recordSuccessfulLogin(email, loginAt);
                    return created;
                });
        users.save(user);
        return oidcUser;
    }
}
