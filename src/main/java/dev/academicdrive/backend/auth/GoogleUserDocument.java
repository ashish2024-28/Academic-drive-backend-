package dev.academicdrive.backend.auth;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("google_users")
public class GoogleUserDocument {
    @Id
    private String googleSubject;

    private String email;
    private Instant createdAt;
    private Instant lastLoginAt;
    private String collegeOrUniversity;
    private DriveConnection driveConnection;

    protected GoogleUserDocument() {
    }

    public GoogleUserDocument(String googleSubject, String email) {
        this.googleSubject = googleSubject;
        this.email = email;
    }

    public void recordSuccessfulLogin(String email, Instant loginAt) {
        this.email = email;
        if (createdAt == null) {
            createdAt = loginAt;
        }
        lastLoginAt = loginAt;
    }

    public void migrateLegacyTimestamps(Instant legacyCreatedAt, Instant legacyLastLoginAt) {
        if (createdAt == null) {
            createdAt = legacyCreatedAt;
        }
        if (lastLoginAt == null) {
            lastLoginAt = legacyLastLoginAt == null ? createdAt : legacyLastLoginAt;
        }
    }

    public void updateCollegeOrUniversity(String collegeOrUniversity) {
        this.collegeOrUniversity = collegeOrUniversity;
    }

    public void updateDriveConnection(
            String rootFolderId, String rootFolderName, Instant verifiedAt) {
        Instant connectedAt = driveConnection == null
                ? verifiedAt
                : driveConnection.connectedAt();
        driveConnection = new DriveConnection(
                rootFolderId, rootFolderName, connectedAt, verifiedAt);
    }

    public void restoreDriveConnection(
            String rootFolderId,
            String rootFolderName,
            Instant connectedAt,
            Instant lastVerifiedAt) {
        driveConnection = new DriveConnection(
                rootFolderId, rootFolderName, connectedAt, lastVerifiedAt);
    }

    public void clearDriveConnection() {
        driveConnection = null;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public String getCollegeOrUniversity() {
        return collegeOrUniversity;
    }

    public DriveConnection getDriveConnection() {
        return driveConnection;
    }

    public record DriveConnection(
            String rootFolderId,
            String rootFolderName,
            Instant connectedAt,
            Instant lastVerifiedAt) {
    }
}
