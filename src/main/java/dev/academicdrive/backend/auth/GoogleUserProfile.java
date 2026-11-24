package dev.academicdrive.backend.auth;

import java.time.Instant;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public record GoogleUserProfile(
        String id,
        String email,
        String name,
        String picture,
        Instant createdAt,
        Instant lastLoginAt,
        String collegeOrUniversity) {
    public static GoogleUserProfile from(OidcUser user, GoogleUserDocument account) {
        String name = user.getFullName();
        return new GoogleUserProfile(
                user.getSubject(),
                user.getEmail(),
                name == null || name.isBlank() ? user.getEmail() : name,
                user.getPicture(),
                account.getCreatedAt(),
                account.getLastLoginAt(),
                account.getCollegeOrUniversity());
    }
}
