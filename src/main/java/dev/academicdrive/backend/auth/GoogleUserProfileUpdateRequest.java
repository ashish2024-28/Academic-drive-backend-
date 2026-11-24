package dev.academicdrive.backend.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GoogleUserProfileUpdateRequest(
        @NotNull(message = "College or university name is required.")
        @Size(max = 200, message = "College or university name must be 200 characters or fewer.")
        String collegeOrUniversity) {
}
