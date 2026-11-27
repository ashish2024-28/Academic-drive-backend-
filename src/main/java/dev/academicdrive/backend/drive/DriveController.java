package dev.academicdrive.backend.drive;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drive")
public class DriveController {
    private final GoogleDriveConnectionService driveConnectionService;
    private final GoogleDriveApiService driveApiService;

    public DriveController(
            GoogleDriveConnectionService driveConnectionService,
            GoogleDriveApiService driveApiService) {
        this.driveConnectionService = driveConnectionService;
        this.driveApiService = driveApiService;
    }

    @GetMapping("/connect")
    public ResponseEntity<Void> connect() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create("/oauth2/authorization/google-drive").toString())
                .build();
    }

    @GetMapping("/status")
    public DriveStatusResponse status(
            Authentication authentication,
            @AuthenticationPrincipal OidcUser user) {
        return driveConnectionService.getStatus(authentication, user);
    }

    @GetMapping("/picker-token")
    public PickerTokenResponse pickerToken(Authentication authentication) {
        return new PickerTokenResponse(driveApiService.pickerAccessToken(authentication));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        driveConnectionService.disconnect(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files")
    public ResponseEntity<byte[]> listFiles(
            Authentication authentication,
            @RequestParam MultiValueMap<String, String> query) {
        return toResponse(driveApiService.listFiles(authentication, query));
    }

    @GetMapping("/about")
    public ResponseEntity<byte[]> about(Authentication authentication) {
        return toResponse(driveApiService.getAbout(authentication));
    }

    @PostMapping("/files")
    public ResponseEntity<byte[]> createFile(
            Authentication authentication,
            @RequestBody byte[] body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) MediaType contentType) {
        return toResponse(driveApiService.createFile(authentication, body, contentType));
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<byte[]> getFile(
            Authentication authentication,
            @PathVariable String fileId,
            @RequestParam MultiValueMap<String, String> query) {
        return toResponse(driveApiService.getFile(authentication, fileId, query));
    }

    @PatchMapping("/files/{fileId}")
    public ResponseEntity<byte[]> updateFile(
            Authentication authentication,
            @PathVariable String fileId,
            @RequestParam MultiValueMap<String, String> query,
            @RequestBody byte[] body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) MediaType contentType) {
        return toResponse(driveApiService.updateFile(
                authentication, fileId, query, body, contentType));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<byte[]> deleteFile(
            Authentication authentication,
            @PathVariable String fileId,
            @RequestParam MultiValueMap<String, String> query) {
        return toResponse(driveApiService.deleteFile(authentication, fileId, query));
    }

    @GetMapping("/files/{fileId}/export")
    public ResponseEntity<byte[]> exportFile(
            Authentication authentication,
            @PathVariable String fileId,
            @RequestParam MultiValueMap<String, String> query) {
        return toResponse(driveApiService.exportFile(authentication, fileId, query));
    }

    @PostMapping(value = "/uploads", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> uploadFile(
            Authentication authentication,
            @RequestParam MultiValueMap<String, String> query,
            @RequestBody byte[] body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) MediaType contentType) {
        return toResponse(driveApiService.uploadFile(authentication, query, body, contentType));
    }

    @PatchMapping("/files/{fileId}/content")
    public ResponseEntity<byte[]> updateFileContent(
            Authentication authentication,
            @PathVariable String fileId,
            @RequestBody byte[] body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) MediaType contentType) {
        return toResponse(driveApiService.updateFileContent(
                authentication, fileId, body, contentType));
    }

    private static ResponseEntity<byte[]> toResponse(DriveApiResponse response) {
        return ResponseEntity.status(response.status())
                .contentType(response.contentType())
                .body(response.body());
    }

    public record PickerTokenResponse(String accessToken) {}
}
