package dev.academicdrive.backend.drive;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriveSyncLockService {
    private static final String COLLECTION = "drive_sync_locks";
    private static final Duration LEASE_DURATION = Duration.ofSeconds(90);

    private final MongoTemplate mongoTemplate;

    public DriveSyncLockService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<String> tryAcquire(String principalName) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Criteria expiredLease = new Criteria().orOperator(
                Criteria.where("expiresAt").lt(now),
                Criteria.where("expiresAt").exists(false));
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(principalName),
                expiredLease));
        Update update = new Update()
                .set("token", token)
                .set("expiresAt", now.plus(LEASE_DURATION));

        try {
            Document lock = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    Document.class,
                    COLLECTION);
            if (lock != null && token.equals(lock.getString("token"))) {
                return Optional.of(token);
            }
        } catch (DuplicateKeyException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public void renew(String principalName, String token) {
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("_id").is(principalName)
                .and("token").is(token)
                .and("expiresAt").gt(now));
        Update update = new Update().set("expiresAt", now.plus(LEASE_DURATION));
        if (mongoTemplate.updateFirst(query, update, COLLECTION).getMatchedCount() != 1) {
            throw lockUnavailable();
        }
    }

    public void release(String principalName, String token) {
        Query query = Query.query(Criteria.where("_id").is(principalName)
                .and("token").is(token));
        mongoTemplate.remove(query, COLLECTION);
    }

    private static ResponseStatusException lockUnavailable() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Another device is syncing this Google Drive account. Please retry in a moment.");
    }
}
