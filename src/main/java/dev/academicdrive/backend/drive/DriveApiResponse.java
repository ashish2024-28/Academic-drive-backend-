package dev.academicdrive.backend.drive;

import org.springframework.http.MediaType;

public record DriveApiResponse(int status, MediaType contentType, byte[] body) {}
