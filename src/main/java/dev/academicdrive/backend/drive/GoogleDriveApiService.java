package dev.academicdrive.backend.drive;

import dev.academicdrive.backend.security.EncryptedAuthorizedClientService;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleDriveApiService {
    private static final Set<String> ALLOWED_QUERY_PARAMETERS = Set.of(
            "q",
            "fields",
            "orderBy",
            "pageSize",
            "pageToken",
            "includeItemsFromAllDrives",
            "supportsAllDrives",
            "alt",
            "mimeType",
            "addParents",
            "removeParents",
            "uploadType",
            "keepRevisionForever");

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final RestClient googleApi;
    private final RestClient googleUploadApi;

    public GoogleDriveApiService(
            OAuth2AuthorizedClientManager authorizedClientManager,
            RestClient.Builder restClientBuilder,
            @Value("${app.google.api-base-url}") String googleApiBaseUrl) {
        this.authorizedClientManager = authorizedClientManager;
        this.googleApi = restClientBuilder.baseUrl(googleApiBaseUrl).build();
        this.googleUploadApi = restClientBuilder.baseUrl(googleApiBaseUrl).build();
    }

    public DriveApiResponse listFiles(
            Authentication authentication, MultiValueMap<String, String> query) {
        return execute(googleApi, authentication, HttpMethod.GET, "/drive/v3/files", query, null, null);
    }

    public DriveApiResponse getAbout(Authentication authentication) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("fields", "storageQuota(limit,usage,usageInDrive,usageInDriveTrash)");
        return execute(googleApi, authentication, HttpMethod.GET, "/drive/v3/about", query, null, null);
    }

    public DriveApiResponse createFile(
            Authentication authentication, byte[] body, MediaType contentType) {
        return execute(
                googleApi,
                authentication,
                HttpMethod.POST,
                "/drive/v3/files",
                null,
                body,
                contentType);
    }

    public DriveApiResponse getFile(
            Authentication authentication,
            String fileId,
            MultiValueMap<String, String> query) {
        return execute(
                googleApi,
                authentication,
                HttpMethod.GET,
                driveFilePath(fileId),
                query,
                null,
                null);
    }

    public DriveApiResponse updateFile(
            Authentication authentication,
            String fileId,
            MultiValueMap<String, String> query,
            byte[] body,
            MediaType contentType) {
        return execute(
                googleApi,
                authentication,
                HttpMethod.PATCH,
                driveFilePath(fileId),
                query,
                body,
                contentType);
    }

    public DriveApiResponse deleteFile(
            Authentication authentication,
            String fileId,
            MultiValueMap<String, String> query) {
        return execute(
                googleApi,
                authentication,
                HttpMethod.DELETE,
                driveFilePath(fileId),
                query,
                null,
                null);
    }

    public DriveApiResponse exportFile(
            Authentication authentication,
            String fileId,
            MultiValueMap<String, String> query) {
        return execute(
                googleApi,
                authentication,
                HttpMethod.GET,
                driveFilePath(fileId) + "/export",
                query,
                null,
                null);
    }

    public DriveApiResponse uploadFile(
            Authentication authentication,
            MultiValueMap<String, String> query,
            byte[] body,
            MediaType contentType) {
        return execute(
                googleUploadApi,
                authentication,
                HttpMethod.POST,
                "/upload/drive/v3/files",
                query,
                body,
                contentType);
    }

    public DriveApiResponse updateFileContent(
            Authentication authentication,
            String fileId,
            byte[] body,
            MediaType contentType) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("uploadType", "media");
        return execute(
                googleUploadApi,
                authentication,
                HttpMethod.PATCH,
                driveFilePath(fileId).replace("/drive/v3", "/upload/drive/v3"),
                query,
                body,
                contentType);
    }

    public String pickerAccessToken(Authentication authentication) {
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(
                                EncryptedAuthorizedClientService.GOOGLE_DRIVE_REGISTRATION_ID)
                        .principal(authentication)
                        .build());
        if (client == null) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED, "Connect Google Drive before opening Google Picker.");
        }
        return client.getAccessToken().getTokenValue();
    }

    private DriveApiResponse execute(
            RestClient api,
            Authentication authentication,
            HttpMethod method,
            String path,
            MultiValueMap<String, String> query,
            byte[] body,
            MediaType contentType) {
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(
                                EncryptedAuthorizedClientService.GOOGLE_DRIVE_REGISTRATION_ID)
                        .principal(authentication)
                        .build());
        if (client == null) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED, "Connect Google Drive before using Drive files.");
        }

        validateQuery(query);
        RestClient.RequestBodySpec request = api.method(method)
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path);
                    if (query != null) builder.queryParams(query);
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        if (body != null) {
            request.contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : contentType)
                    .body(body);
        }

        ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);
        MediaType responseContentType = response.getHeaders().getContentType();
        return new DriveApiResponse(
                response.getStatusCode().value(),
                responseContentType == null ? MediaType.APPLICATION_JSON : responseContentType,
                response.getBody() == null ? new byte[0] : response.getBody());
    }

    private static void validateQuery(MultiValueMap<String, String> query) {
        if (query == null) return;
        for (String parameter : query.keySet()) {
            if (!ALLOWED_QUERY_PARAMETERS.contains(parameter)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unsupported Google Drive query parameter.");
            }
        }
    }

    private static String driveFilePath(String fileId) {
        if (fileId == null || !fileId.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Google Drive file ID.");
        }
        return "/drive/v3/files/" + fileId;
    }
}
