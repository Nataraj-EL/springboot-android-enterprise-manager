package com.example.androidmanagementpoc.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service component responsible for handling Google APIs authentication.
 * It loads the local service account private key JSON and initiates a secure 
 * OAuth 2.0 handshake to obtain access tokens authorized for device management.
 */
@Service
public class GoogleAuthService {

    @Value("${google.android.management.service-account-path}")
    private String serviceAccountKeyPath;

    private static final String ANDROID_MANAGEMENT_SCOPE = "https://www.googleapis.com/auth/androidmanagement";

    /**
     * Loads the configured Google Service Account private key JSON and initiates 
     * a secure OAuth 2.0 handshake to obtain a valid access token.
     *
     * @return String the OAuth 2.0 Access Token value to authenticate management API requests
     * @throws IOException if the service account file cannot be loaded, read, or validated
     */
    public String getAccessToken() throws IOException {
        System.out.println("Loading Google Credentials from path: " + serviceAccountKeyPath);
        
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(serviceAccountKeyPath))
                .createScoped(Collections.singleton(ANDROID_MANAGEMENT_SCOPE));
        
        credentials.refreshIfExpired();
        AccessToken token = credentials.getAccessToken();
        
        System.out.println("Successfully generated OAuth 2.0 access token.");
        return token.getTokenValue();
    }

    /**
     * Reads the configured Service Account JSON file programmatically and extracts 
     * the project_id string required for targeting resource segments.
     *
     * @return String the unique Google Cloud project_id
     * @throws IOException if the service account file cannot be read
     * @throws RuntimeException if the project_id key cannot be parsed from the JSON payload
     */
    public String getProjectId() throws IOException {
        Path path = Paths.get(serviceAccountKeyPath);
        String content = Files.readString(path);
        
        Matcher matcher = Pattern.compile("\"project_id\"\\s*:\\s*\"([^\"]+)\"").matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new RuntimeException("Could not extract project_id from service account key at path: " + serviceAccountKeyPath);
    }
}
