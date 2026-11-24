package dev.academicdrive.backend.security;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoredAuthorizedClientRepository extends MongoRepository<StoredAuthorizedClient, String> {
    Optional<StoredAuthorizedClient> findByClientRegistrationIdAndPrincipalName(
            String clientRegistrationId, String principalName);

    void deleteByClientRegistrationIdAndPrincipalName(String clientRegistrationId, String principalName);
}
