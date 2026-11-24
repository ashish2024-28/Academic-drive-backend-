package dev.academicdrive.backend.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.academicdrive.backend.auth.GoogleUserDocument;
import dev.academicdrive.backend.auth.GoogleUserRepository;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;

class LegacyDriveConnectionMigrationTest {
    @Test
    void stripsLegacyProfileFieldsAndMovesPreviousUpdateTimeToLastLogin() {
        DriveConnectionRepository legacyConnections = mock(DriveConnectionRepository.class);
        GoogleUserRepository users = mock(GoogleUserRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        Instant createdAt = Instant.ofEpochMilli(1_800_000_000_000L);
        Instant lastLoginAt = createdAt.plusSeconds(60);
        Document legacyProfile = new Document("_id", "subject-1")
                .append("email", "student@example.com")
                .append("name", "Student")
                .append("pictureUrl", "https://example.com/avatar.png")
                .append("createdAt", Date.from(createdAt))
                .append("updatedAt", Date.from(lastLoginAt));
        GoogleUserDocument user =
                new GoogleUserDocument("subject-1", "student@example.com");
        when(mongoTemplate.findAll(Document.class, "google_users"))
                .thenReturn(List.of(legacyProfile));
        when(legacyConnections.findAll()).thenReturn(List.of());
        when(users.findById("subject-1")).thenReturn(Optional.of(user));

        LegacyDriveConnectionMigration migration =
                new LegacyDriveConnectionMigration(legacyConnections, users, mongoTemplate);
        migration.run(mock(ApplicationArguments.class));

        assertEquals(createdAt, user.getCreatedAt());
        assertEquals(lastLoginAt, user.getLastLoginAt());
        verify(users).save(user);
    }

    @Test
    void movesMatchingLegacyConnectionIntoUserAndDeletesLegacyDocument() {
        DriveConnectionRepository legacyConnections = mock(DriveConnectionRepository.class);
        GoogleUserRepository users = mock(GoogleUserRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        Instant now = Instant.now();
        GoogleUserDocument user = new GoogleUserDocument("subject-1", "student@example.com");
        user.recordSuccessfulLogin("student@example.com", now);
        DriveConnectionDocument legacy = new DriveConnectionDocument(
                "subject-1", "student@example.com", "folder-1", "academic drive", now, now);
        when(mongoTemplate.findAll(Document.class, "google_users")).thenReturn(List.of());
        when(legacyConnections.findAll()).thenReturn(List.of(legacy));
        when(users.findById("subject-1")).thenReturn(Optional.of(user));

        LegacyDriveConnectionMigration migration =
                new LegacyDriveConnectionMigration(legacyConnections, users, mongoTemplate);
        migration.run(mock(ApplicationArguments.class));

        assertNotNull(user.getDriveConnection());
        assertEquals("folder-1", user.getDriveConnection().rootFolderId());
        assertEquals("academic drive", user.getDriveConnection().rootFolderName());
        verify(users).save(user);
        verify(legacyConnections).deleteById("subject-1");
    }

    @Test
    void retainsLegacyConnectionWhenItsEmailDoesNotMatchTheUser() {
        DriveConnectionRepository legacyConnections = mock(DriveConnectionRepository.class);
        GoogleUserRepository users = mock(GoogleUserRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        Instant now = Instant.now();
        GoogleUserDocument user = new GoogleUserDocument("subject-1", "student@example.com");
        user.recordSuccessfulLogin("student@example.com", now);
        DriveConnectionDocument legacy = new DriveConnectionDocument(
                "subject-1", "different@example.com", "folder-1", "academic drive", now, now);
        when(mongoTemplate.findAll(Document.class, "google_users")).thenReturn(List.of());
        when(legacyConnections.findAll()).thenReturn(List.of(legacy));
        when(users.findById("subject-1")).thenReturn(Optional.of(user));

        LegacyDriveConnectionMigration migration =
                new LegacyDriveConnectionMigration(legacyConnections, users, mongoTemplate);
        migration.run(mock(ApplicationArguments.class));

        verify(users, never()).save(user);
        verify(legacyConnections, never()).deleteById("subject-1");
    }
}
