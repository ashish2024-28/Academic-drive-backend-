package dev.academicdrive.backend.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GoogleUserRepository extends MongoRepository<GoogleUserDocument, String> {
}
