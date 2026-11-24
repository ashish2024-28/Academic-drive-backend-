package dev.academicdrive.backend.auth;

import dev.academicdrive.backend.drive.GoogleDriveConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final GoogleUserRepository users;
    private final GoogleDriveConnectionService driveConnectionService;

    public AuthController(
            GoogleUserRepository users,
            GoogleDriveConnectionService driveConnectionService) {
        this.users = users;
        this.driveConnectionService = driveConnectionService;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @GetMapping("/me")
    public ResponseEntity<GoogleUserProfile> currentUser(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return ResponseEntity.noContent().build();
        }
        GoogleUserDocument account = findAccount(user);
        return ResponseEntity.ok(GoogleUserProfile.from(user, account));
    }

    @PatchMapping("/profile")
    public GoogleUserProfile updateProfile(
            Authentication authentication,
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody GoogleUserProfileUpdateRequest request) {
        GoogleUserDocument account = findAccount(user);
        String collegeOrUniversity = request.collegeOrUniversity().trim();
        if (collegeOrUniversity.isEmpty()) {
            collegeOrUniversity = null;
        }

        driveConnectionService.synchronizeCollegeDescription(
                authentication, account, collegeOrUniversity);
        account.updateCollegeOrUniversity(collegeOrUniversity);
        users.save(account);
        return GoogleUserProfile.from(user, account);
    }

    private GoogleUserDocument findAccount(OidcUser user) {
        return users.findById(user.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "The signed-in Google profile is missing from persistence."));
    }
}
