package dev.academicdrive.backend.drive;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("drive_connections")
public class DriveConnectionDocument {
    @Id
    private String googleSubject;

    private String email;
    private String rootFolderId;
    private String rootFolderName;
    private Instant connectedAt;
    private Instant lastVerifiedAt;

    protected DriveConnectionDocument() {
    }

    public DriveConnectionDocument(
            String googleSubject,
            String email,
            String rootFolderId,
            String rootFolderName,
            Instant connectedAt,
            Instant lastVerifiedAt) {
        this.googleSubject = googleSubject;
        this.email = email;
        this.rootFolderId = rootFolderId;
        this.rootFolderName = rootFolderName;
        this.connectedAt = connectedAt;
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public void updateVerification(String email, String rootFolderId, String rootFolderName) {
        this.email = email;
        this.rootFolderId = rootFolderId;
        this.rootFolderName = rootFolderName;
        lastVerifiedAt = Instant.now();
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getRootFolderId() {
        return rootFolderId;
    }

    public String getRootFolderName() {
        return rootFolderName;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }
}
