package dev.academicdrive.backend.drive;

import java.time.Instant;

public record DriveStatusResponse(
        boolean connected,
        String email,
        String rootFolderId,
        String rootFolderName,
        Instant connectedAt,
        Instant lastVerifiedAt,
        Instant accessTokenExpiresAt) {
    public static DriveStatusResponse disconnected() {
        return new DriveStatusResponse(false, null, null, null, null, null, null);
    }
}
