package com.example.androidmanagementpoc.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

@Service
public class GoogleAuthService {

    // IMPORTANT: Place your service account JSON file in the project directory
    private static final String SERVICE_ACCOUNT_KEY_PATH = "service-account.json"; 
    private static final String ANDROID_MANAGEMENT_SCOPE = "https://www.googleapis.com/auth/androidmanagement";

    /**
     * Loads the service account JSON and generates an OAuth access token.
     * 
     * @return String AccessToken for authenticating API requests
     * @throws IOException if service account file is not found or invalid
     */
    public String getAccessToken() throws IOException {
        System.out.println("Loading Google Credentials from: " + SERVICE_ACCOUNT_KEY_PATH);
        
        // Authenticate with Google using a Service Account JSON
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(SERVICE_ACCOUNT_KEY_PATH))
                .createScoped(Collections.singleton(ANDROID_MANAGEMENT_SCOPE));
        
        credentials.refreshIfExpired();
        AccessToken token = credentials.getAccessToken();
        
        System.out.println("Successfully generated OAuth access token.");
        return token.getTokenValue();
    }

    /**
     * Reads the service account JSON and extracts the project_id.
     * 
     * @return String Project ID
     * @throws IOException if service account file is not found or invalid
     */
    public String getProjectId() throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get(SERVICE_ACCOUNT_KEY_PATH);
        String content = java.nio.file.Files.readString(path);
        
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"project_id\"\\s*:\\s*\"([^\"]+)\"").matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new RuntimeException("Could not find project_id in " + SERVICE_ACCOUNT_KEY_PATH);
    }
}
