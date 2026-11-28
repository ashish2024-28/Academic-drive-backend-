package dev.academicdrive.backend.drive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class DriveSyncLockServiceTest {
    @Test
    void acquiresLeaseWithGeneratedToken() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Document.class),
                eq("drive_sync_locks")))
                .thenAnswer(invocation -> {
                    Update update = invocation.getArgument(1);
                    Document setValues = (Document) update.getUpdateObject().get("$set");
                    return new Document("token", setValues.getString("token"));
                });
        DriveSyncLockService service = new DriveSyncLockService(mongoTemplate);

        String token = service.tryAcquire("google-subject").orElseThrow();

        assertFalse(token.isBlank());
    }

    @Test
    void reportsWhenAnotherDeviceOwnsTheLease() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Document.class),
                eq("drive_sync_locks")))
                .thenReturn(null);
        DriveSyncLockService service = new DriveSyncLockService(mongoTemplate);

        var token = service.tryAcquire("google-subject");

        assertTrue(token.isEmpty());
    }
}
