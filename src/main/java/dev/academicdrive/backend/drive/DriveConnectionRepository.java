package dev.academicdrive.backend.drive;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DriveConnectionRepository extends MongoRepository<DriveConnectionDocument, String> {
}
