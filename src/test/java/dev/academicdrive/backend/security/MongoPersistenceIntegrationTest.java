package dev.academicdrive.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.academicdrive.backend.auth.GoogleUserDocument;
import dev.academicdrive.backend.auth.GoogleUserRepository;
import dev.academicdrive.backend.drive.DriveConnectionDocument;
import dev.academicdrive.backend.drive.DriveConnectionRepository;
import dev.academicdrive.backend.drive.LegacyDriveConnectionMigration;
import dev.academicdrive.backend.security.StoredAuthorizedClientRepository;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.bson.Document;

@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = "mongodb://.+")
class MongoPersistenceIntegrationTest {
    @Autowired
    private GoogleUserRepository users;

    @Autowired
    private StoredAuthorizedClientRepository authorizedClients;

    @Autowired
    private DriveConnectionRepository driveConnections;

    @Autowired
    private MongoTemplate mongoTemplate;

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
    }

    @BeforeEach
    void clearIsolatedTestCollections() {
        users.deleteAll();
        authorizedClients.deleteAll();
        driveConnections.deleteAll();
    }

    @AfterEach
    void removeIntegrationTestRecords() {
        users.deleteAll();
        authorizedClients.deleteAll();
        driveConnections.deleteAll();
    }

    @Test
    void migratesLegacyDriveConnectionIntoSingleUserDocument() {
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        Instant createdAt = now.minusSeconds(60);
        mongoTemplate.getCollection("google_users").insertOne(new Document("_id", "google-subject")
                .append("email", "user@example.com")
                .append("name", "Study User")
                .append("pictureUrl", "https://example.com/avatar.png")
                .append("createdAt", Date.from(createdAt))
                .append("updatedAt", Date.from(now)));

        StoredAuthorizedClient authorizedClient = new StoredAuthorizedClient();
        authorizedClient.initialize("google-drive", "google-subject");
        authorizedClient.updateAccessToken(
                "v1:encrypted-access-token", now, now.plusSeconds(3600), "drive.file");
        authorizedClient.updateRefreshToken("v1:encrypted-refresh-token", now);
        authorizedClients.save(authorizedClient);

        driveConnections.save(new DriveConnectionDocument(
                "google-subject",
                "user@example.com",
                "drive-folder-id",
                "academic drive",
                now,
                now));

        new LegacyDriveConnectionMigration(driveConnections, users, mongoTemplate)
                .run(new DefaultApplicationArguments());
        GoogleUserDocument migratedUser = users.findById("google-subject").orElseThrow();
        assertEquals("user@example.com", migratedUser.getEmail());
        assertEquals(createdAt, migratedUser.getCreatedAt());
        assertEquals(now, migratedUser.getLastLoginAt());
        assertNotNull(migratedUser.getDriveConnection());
        assertEquals("drive-folder-id", migratedUser.getDriveConnection().rootFolderId());
        assertEquals("academic drive", migratedUser.getDriveConnection().rootFolderName());
        assertEquals(now, migratedUser.getDriveConnection().connectedAt());
        assertEquals(now, migratedUser.getDriveConnection().lastVerifiedAt());
        assertFalse(driveConnections.findById("google-subject").isPresent());

        Document storedUser = mongoTemplate.getCollection("google_users")
                .find(new Document("_id", "google-subject"))
                .first();
        assertNotNull(storedUser);
        assertFalse(storedUser.containsKey("name"));
        assertFalse(storedUser.containsKey("pictureUrl"));
        assertFalse(storedUser.containsKey("updatedAt"));
        Document storedDrive = (Document) storedUser.get("driveConnection");
        assertFalse(storedDrive.containsKey("email"));

        assertEquals(
                "v1:encrypted-access-token",
                authorizedClients.findByClientRegistrationIdAndPrincipalName(
                                "google-drive", "google-subject")
                        .orElseThrow()
                        .getAccessTokenCiphertext());
    }
}
