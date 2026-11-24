package dev.academicdrive.backend.drive;

import dev.academicdrive.backend.auth.GoogleUserDocument;
import dev.academicdrive.backend.auth.GoogleUserRepository;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class LegacyDriveConnectionMigration implements ApplicationRunner {
    private static final Logger logger =
            LoggerFactory.getLogger(LegacyDriveConnectionMigration.class);

    private final DriveConnectionRepository legacyConnections;
    private final GoogleUserRepository users;
    private final MongoTemplate mongoTemplate;

    public LegacyDriveConnectionMigration(
            DriveConnectionRepository legacyConnections,
            GoogleUserRepository users,
            MongoTemplate mongoTemplate) {
        this.legacyConnections = legacyConnections;
        this.users = users;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateLegacyUserFields();
        for (DriveConnectionDocument legacy : legacyConnections.findAll()) {
            migrate(legacy);
        }
    }

    private void migrateLegacyUserFields() {
        for (Document stored : mongoTemplate.findAll(Document.class, "google_users")) {
            if (!stored.containsKey("name")
                    && !stored.containsKey("pictureUrl")
                    && !stored.containsKey("updatedAt")
                    && stored.containsKey("lastLoginAt")) {
                continue;
            }

            String googleSubject = stored.getString("_id");
            GoogleUserDocument user = users.findById(googleSubject).orElse(null);
            if (user == null) {
                logger.error("A legacy Google profile could not be loaded for migration; it was retained.");
                continue;
            }
            user.migrateLegacyTimestamps(
                    toInstant(stored.get("createdAt")),
                    toInstant(stored.get("updatedAt")));
            if (user.getCreatedAt() == null || user.getLastLoginAt() == null) {
                logger.error("A legacy Google profile has no usable timestamps; it was retained.");
                continue;
            }
            users.save(user);
        }
    }

    private void migrate(DriveConnectionDocument legacy) {
        GoogleUserDocument user = users.findById(legacy.getGoogleSubject()).orElse(null);
        if (user == null) {
            logger.error("A legacy Drive connection has no matching Google profile; it was retained.");
            return;
        }
        if (!sameEmail(user.getEmail(), legacy.getEmail())) {
            logger.error("A legacy Drive connection email does not match its Google profile; it was retained.");
            return;
        }

        GoogleUserDocument.DriveConnection current = user.getDriveConnection();
        if (current == null) {
            user.restoreDriveConnection(
                    legacy.getRootFolderId(),
                    legacy.getRootFolderName(),
                    legacy.getConnectedAt(),
                    legacy.getLastVerifiedAt());
            users.save(user);
        } else if (!Objects.equals(current.rootFolderId(), legacy.getRootFolderId())) {
            logger.error("A legacy Drive connection conflicts with the embedded user connection; it was retained.");
            return;
        }

        legacyConnections.deleteById(legacy.getGoogleSubject());
    }

    private static boolean sameEmail(String first, String second) {
        return first != null && second != null
                && first.trim().equalsIgnoreCase(second.trim());
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }
}
