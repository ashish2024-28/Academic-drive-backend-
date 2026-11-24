package dev.academicdrive.backend.drive;

import dev.academicdrive.backend.auth.GoogleUserDocument;
import dev.academicdrive.backend.auth.GoogleUserRepository;
import dev.academicdrive.backend.security.EncryptedAuthorizedClientService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleDriveConnectionService {
    private static final String REGISTRATION_ID =
            EncryptedAuthorizedClientService.GOOGLE_DRIVE_REGISTRATION_ID;
    private static final String ROOT_FOLDER_NAME = "academic drive";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String COLLEGE_DESCRIPTION_PREFIX =
            "Academic Drive - College / University: ";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GoogleUserRepository users;
    private final RestClient googleApi;
    private final RestClient tokenRevocationApi;
    private final String userInfoUrl;

    public GoogleDriveConnectionService(
            OAuth2AuthorizedClientManager authorizedClientManager,
            OAuth2AuthorizedClientService authorizedClientService,
            GoogleUserRepository users,
            RestClient.Builder restClientBuilder,
            @Value("${app.google.api-base-url}") String googleApiBaseUrl,
            @Value("${app.google.userinfo-url}") String userInfoUrl,
            @Value("${app.google.token-revocation-url}") String tokenRevocationUrl) {
        this.authorizedClientManager = authorizedClientManager;
        this.authorizedClientService = authorizedClientService;
        this.users = users;
        this.googleApi = restClientBuilder.baseUrl(googleApiBaseUrl).build();
        this.tokenRevocationApi = restClientBuilder.baseUrl(tokenRevocationUrl).build();
        this.userInfoUrl = userInfoUrl;
    }

    public DriveStatusResponse getStatus(Authentication authentication, OidcUser user) {
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                        .principal(authentication)
                        .build());
        if (client == null) {
            clearDriveConnection(user.getSubject());
            return DriveStatusResponse.disconnected();
        }

        String driveEmail = getAuthorizedEmail(client.getAccessToken());
        if (!sameEmail(user.getEmail(), driveEmail)) {
            disconnect(authentication.getName(), client);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Connect Google Drive with the same Google account used to sign in.");
        }

        DriveFolder rootFolder = findRootFolder(client.getAccessToken());
        if (rootFolder == null) {
            rootFolder = createRootFolder(client.getAccessToken());
        }
        DriveFolder verifiedRootFolder = rootFolder;

        GoogleUserDocument account = users.findById(user.getSubject())
                .orElseThrow(() -> new IllegalStateException(
                        "The signed-in Google profile is missing from persistence."));
        synchronizeCollegeDescription(
                client.getAccessToken(), verifiedRootFolder, account.getCollegeOrUniversity());
        Instant verifiedAt = Instant.now();
        account.updateDriveConnection(
                verifiedRootFolder.id(), verifiedRootFolder.name(), verifiedAt);
        users.save(account);
        GoogleUserDocument.DriveConnection connection = account.getDriveConnection();

        return new DriveStatusResponse(
                true,
                driveEmail,
                connection.rootFolderId(),
                connection.rootFolderName(),
                connection.connectedAt(),
                connection.lastVerifiedAt(),
                client.getAccessToken().getExpiresAt());
    }

    public void synchronizeCollegeDescription(
            Authentication authentication,
            GoogleUserDocument account,
            String collegeOrUniversity) {
        GoogleUserDocument.DriveConnection connection = account.getDriveConnection();
        if (connection == null) {
            return;
        }

        OAuth2AuthorizedClient client = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                        .principal(authentication)
                        .build());
        if (client == null) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "Reconnect Google Drive before saving the college or university name.");
        }
        String driveEmail = getAuthorizedEmail(client.getAccessToken());
        if (!sameEmail(account.getEmail(), driveEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Connect Google Drive with the same Google account used to sign in.");
        }

        DriveFolder folder = getDriveFolder(client.getAccessToken(), connection.rootFolderId());
        if (folder == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The connected Google Drive folder could not be found.");
        }
        synchronizeCollegeDescription(client.getAccessToken(), folder, collegeOrUniversity);
    }

    public void disconnect(String principalName) {
        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient(REGISTRATION_ID, principalName);
        disconnect(principalName, client);
    }

    private void disconnect(String principalName, OAuth2AuthorizedClient client) {
        if (client != null) {
            OAuth2RefreshToken refreshToken = client.getRefreshToken();
            OAuth2AccessToken accessToken = client.getAccessToken();
            String tokenToRevoke = refreshToken == null
                    ? accessToken.getTokenValue()
                    : refreshToken.getTokenValue();
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", tokenToRevoke);
            tokenRevocationApi.post()
                    .uri("")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        }
        authorizedClientService.removeAuthorizedClient(REGISTRATION_ID, principalName);
        clearDriveConnection(principalName);
    }

    private void clearDriveConnection(String googleSubject) {
        users.findById(googleSubject).ifPresent(user -> {
            if (user.getDriveConnection() != null) {
                user.clearDriveConnection();
                users.save(user);
            }
        });
    }

    private String getAuthorizedEmail(OAuth2AccessToken accessToken) {
        Map<String, Object> claims = googleApi.get()
                .uri(userInfoUrl)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        Object email = claims == null ? null : claims.get("email");
        Object verified = claims == null ? null : claims.get("email_verified");
        if (!(email instanceof String emailValue)
                || !(verified instanceof Boolean isVerified)
                || !isVerified) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Google did not verify the Drive account email.");
        }
        return emailValue;
    }

    private DriveFolder findRootFolder(OAuth2AccessToken accessToken) {
        String escapedName = ROOT_FOLDER_NAME.replace("\\", "\\\\").replace("'", "\\'");
        String query = "name = '" + escapedName + "' and mimeType = '" + FOLDER_MIME_TYPE
                + "' and 'root' in parents and trashed = false";
        DriveFilesResponse response = googleApi.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/drive/v3/files")
                        .queryParam("q", query)
                        .queryParam("fields", "files(id,name,mimeType,webViewLink,description)")
                        .queryParam("pageSize", 10)
                        .queryParam("supportsAllDrives", true)
                        .queryParam("includeItemsFromAllDrives", true)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(DriveFilesResponse.class);
        List<DriveFolder> files = response == null || response.files() == null
                ? List.of()
                : response.files();
        return files.stream().findFirst().orElse(null);
    }

    private DriveFolder getDriveFolder(OAuth2AccessToken accessToken, String folderId) {
        return googleApi.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/drive/v3/files/{folderId}")
                        .queryParam("fields", "id,name,mimeType,webViewLink,description")
                        .queryParam("supportsAllDrives", true)
                        .build(folderId))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(DriveFolder.class);
    }

    private void synchronizeCollegeDescription(
            OAuth2AccessToken accessToken, DriveFolder folder, String collegeOrUniversity) {
        String description = replaceCollegeDescription(
                folder.description(), collegeOrUniversity);
        if (sameDescription(folder.description(), description)) {
            return;
        }

        googleApi.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/drive/v3/files/{folderId}")
                        .queryParam("fields", "id")
                        .queryParam("supportsAllDrives", true)
                        .build(folder.id()))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DriveFolderDescription(description == null ? "" : description))
                .retrieve()
                .toBodilessEntity();
    }

    private static String replaceCollegeDescription(
            String description, String collegeOrUniversity) {
        List<String> lines = new ArrayList<>();
        if (description != null && !description.isBlank()) {
            description.lines()
                    .filter(line -> !line.startsWith(COLLEGE_DESCRIPTION_PREFIX))
                    .forEach(lines::add);
        }
        if (collegeOrUniversity != null && !collegeOrUniversity.isBlank()) {
            lines.add(COLLEGE_DESCRIPTION_PREFIX + collegeOrUniversity);
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private static boolean sameDescription(String first, String second) {
        return Objects.equals(
                first == null || first.isBlank() ? null : first,
                second == null || second.isBlank() ? null : second);
    }

    private DriveFolder createRootFolder(OAuth2AccessToken accessToken) {
        DriveFolder created = googleApi.post()
                .uri("/drive/v3/files?supportsAllDrives=true")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateDriveFolder(ROOT_FOLDER_NAME, FOLDER_MIME_TYPE))
                .retrieve()
                .body(DriveFolder.class);
        if (created == null || created.id() == null || created.id().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Google Drive did not return the new folder.");
        }
        return created;
    }

    private static String bearer(OAuth2AccessToken token) {
        return "Bearer " + token.getTokenValue();
    }

    private static boolean sameEmail(String first, String second) {
        return first != null && second != null
                && first.trim().equalsIgnoreCase(second.trim());
    }

    private record DriveFilesResponse(List<DriveFolder> files) {}

    private record DriveFolder(
            String id, String name, String mimeType, String webViewLink, String description) {}

    private record CreateDriveFolder(String name, String mimeType) {}

    private record DriveFolderDescription(String description) {}
}
