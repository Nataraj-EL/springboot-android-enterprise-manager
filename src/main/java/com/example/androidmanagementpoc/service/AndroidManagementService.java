package com.example.androidmanagementpoc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Service component handling operations with the Google Android Management API (v1).
 * It exposes methods to programmatic enterprise lists, policy syncs, enrollment token
 * generation, file output extraction, and device/active token auditing.
 */
@Service
public class AndroidManagementService {

    @Autowired
    private GoogleAuthService googleAuthService;

    @Value("${google.android.management.enterprise-id}")
    private String enterpriseId;

    @Value("${google.android.management.policy-id}")
    private String policyId;

    /**
     * Builds the target policy sync API Endpoint URL dynamically.
     *
     * @return String Endpoint URL
     */
    private String getPolicyApiUrl() {
        return "https://androidmanagement.googleapis.com/v1/enterprises/" 
                + enterpriseId + "/policies/" + policyId;
    }

    /**
     * Synchronizes a default device policy with Google's servers.
     * This ensures the target policy exists before device enrollment is attempted.
     *
     * @param accessToken OAuth 2.0 access token
     * @throws Exception if policy synchronization fails or is rejected
     */
    public void syncDefaultPolicy(String accessToken) throws Exception {
        System.out.println("\n=== Synchronizing Default Policy ===");
        String policyJson = "{\n" +
                "  \"applications\": [\n" +
                "    {\n" +
                "      \"packageName\": \"com.whatsapp\",\n" +
                "      \"installType\": \"FORCE_INSTALLED\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"cameraDisabled\": false\n" +
                "}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getPolicyApiUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(policyJson))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("API Response Status: " + response.statusCode());
        System.out.println("API Response Body: " + response.body());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Policy synchronization failed with status code " + response.statusCode() + ": " + response.body());
        }
        System.out.println("Default policy successfully synchronized on Google servers.");
        System.out.println("====================================\n");
    }

    /**
     * Programmatically requests a new short-lived enrollment token bound 
     * to the default policy and writes provisioning files locally.
     *
     * @param accessToken OAuth 2.0 access token
     * @throws Exception if token generation is rejected
     */
    public void createEnrollmentToken(String accessToken) throws Exception {
        System.out.println("\n=== Generating Enrollment Token ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises/" + enterpriseId + "/enrollmentTokens";
        
        String jsonBody = "{\n" +
                "  \"policyName\": \"enterprises/" + enterpriseId + "/policies/" + policyId + "\"\n" +
                "}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("API Response Body:");
        System.out.println(response.body());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Enrollment token generation failed with status code " + response.statusCode() + ": " + response.body());
        }

        writeEnrollmentFiles(response.body());

        System.out.println("============================\n");
    }

    /**
     * Helper parser utilizing regex groups to extract 'value' and 'qrCode' payloads 
     * from token responses and unescapes the QR payload into raw, clean local JSON.
     *
     * @param responseBody Raw API HTTP response body
     */
    private void writeEnrollmentFiles(String responseBody) {
        try {
            java.util.regex.Matcher valueMatcher = java.util.regex.Pattern.compile("\"value\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(responseBody);
            if (valueMatcher.find()) {
                String tokenValue = valueMatcher.group(1);
                java.nio.file.Files.writeString(java.nio.file.Paths.get("enrollment-token.txt"), tokenValue);
                System.out.println("Generated enrollment-token.txt at: " + java.nio.file.Paths.get("enrollment-token.txt").toAbsolutePath());
            }
            
            java.util.regex.Matcher qrMatcher = java.util.regex.Pattern.compile("\"qrCode\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(responseBody);
            if (qrMatcher.find()) {
                String rawQr = qrMatcher.group(1);
                String unescapedQr = rawQr.replace("\\\"", "\"").replace("\\\\", "\\");
                java.nio.file.Files.writeString(java.nio.file.Paths.get("enrollment-qr.json"), unescapedQr);
                System.out.println("Generated enrollment-qr.json at: " + java.nio.file.Paths.get("enrollment-qr.json").toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Error writing enrollment files: " + e.getMessage());
        }
    }

    /**
     * Programmatically requests signup URLs linked to the GCP project.
     *
     * @param accessToken OAuth 2.0 access token
     * @param projectId unique GCP project ID
     * @throws Exception if signup URL generation fails
     */
    public void createSignupUrl(String accessToken, String projectId) throws Exception {
        System.out.println("\n=== Generating Signup URL ===");
        String url = "https://androidmanagement.googleapis.com/v1/signupUrls?projectId=" + projectId;
        String jsonBody = "{\"callbackUrl\": \"https://localhost:8080\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("API Response Body:");
        System.out.println(response.body());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Signup URL generation failed with status code " + response.statusCode() + ": " + response.body());
        }
        System.out.println("============================\n");
    }

    /**
     * Lists active linked enterprises associated with the target GCP project.
     *
     * @param accessToken OAuth 2.0 access token
     * @param projectId unique GCP project ID
     * @throws Exception if enterprises lookup fails
     */
    public void listEnterprises(String accessToken, String projectId) throws Exception {
        System.out.println("\n=== Fetching Enterprises ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises?projectId=" + projectId;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Listing enterprises failed with status code " + response.statusCode() + ": " + response.body());
        }
        System.out.println("============================\n");
    }

    /**
     * Lists all enrolled devices in the linked enterprise segment.
     *
     * @param accessToken OAuth 2.0 access token
     * @throws Exception if devices listing fails
     */
    public void listDevices(String accessToken) throws Exception {
        System.out.println("\n=== Listing Devices ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises/" + enterpriseId + "/devices";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Listing devices failed with status code " + response.statusCode() + ": " + response.body());
        }
        System.out.println("============================\n");
    }

    /**
     * Lists all live policies configured on Google's servers for the enterprise.
     *
     * @param accessToken OAuth 2.0 access token
     * @throws Exception if policies listing fails
     */
    public void listPolicies(String accessToken) throws Exception {
        System.out.println("\n=== Listing Policies ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises/" + enterpriseId + "/policies";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Listing policies failed with status code " + response.statusCode() + ": " + response.body());
        }
        System.out.println("============================\n");
    }

    /**
     * Lists and parses all active, unexpired enrollment tokens for the enterprise.
     * Extracts token count, expiration timestamps, and policy bindings.
     *
     * @param accessToken OAuth 2.0 access token
     * @throws Exception if listing enrollment tokens fails
     */
    public void listEnrollmentTokens(String accessToken) throws Exception {
        System.out.println("\n=== Listing Enrollment Tokens ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises/" + enterpriseId + "/enrollmentTokens";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Listing enrollment tokens failed with status code " + response.statusCode() + ": " + response.body());
        }

        String body = response.body();
        System.out.println("API Response Body:");
        System.out.println(body);

        int tokenCount = 0;
        java.util.regex.Matcher nameMatcher = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"(enterprises/[^\"]+/enrollmentTokens/[^\"]+)\"").matcher(body);
        
        java.util.List<Integer> nameIndices = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        while (nameMatcher.find()) {
            nameIndices.add(nameMatcher.start());
            names.add(nameMatcher.group(1));
        }

        tokenCount = names.size();
        for (int i = 0; i < tokenCount; i++) {
            int startIdx = nameIndices.get(i);
            int endIdx = (i + 1 < tokenCount) ? nameIndices.get(i + 1) : body.length();
            String tokenJson = body.substring(startIdx, endIdx);

            String expiration = "N/A";
            java.util.regex.Matcher expMatcher = java.util.regex.Pattern.compile("\"expirationTimestamp\"\\s*:\\s*\"([^\"]+)\"").matcher(tokenJson);
            if (expMatcher.find()) {
                expiration = expMatcher.group(1);
            }

            String policy = "N/A";
            java.util.regex.Matcher polMatcher = java.util.regex.Pattern.compile("\"policyName\"\\s*:\\s*\"([^\"]+)\"").matcher(tokenJson);
            if (polMatcher.find()) {
                policy = polMatcher.group(1);
            }

            System.out.println("Token #" + (i + 1) + ":");
            System.out.println("  - Name: " + names.get(i));
            System.out.println("  - Expiration time: " + expiration);
            System.out.println("  - Policy name: " + policy);
        }
        
        System.out.println("Total Enrollment Token Count: " + tokenCount);
        System.out.println("================================\n");
    }

    /**
     * Executes a management command (e.g. installing/blocking apps, disabling camera)
     * by patching policy settings on Google's API servers.
     *
     * @param command Command type identifier
     * @throws Exception if the command execution fails
     */
    public void executeCommand(String command) throws Exception {
        String policyJson = generatePolicyJson(command);
        System.out.println("Generated Policy JSON:\n" + policyJson);

        String accessToken = googleAuthService.getAccessToken();
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getPolicyApiUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(policyJson))
                .build();
                
        System.out.println("Sending policy to Android Management API Endpoint...");
        System.out.println("Endpoint: " + getPolicyApiUrl());
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("API Response Status: " + response.statusCode());
        System.out.println("API Response Body: " + response.body());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("API Request failed with status code: " + response.statusCode());
        }
    }

    /**
     * Generates state-based policy JSON configurations programmatically.
     *
     * @param command Command type
     * @return String Policy JSON payload
     */
    private String generatePolicyJson(String command) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");

        switch (command.toUpperCase()) {
            case "INSTALL_APP":
                jsonBuilder.append("  \"applications\": [\n");
                jsonBuilder.append("    {\n");
                jsonBuilder.append("      \"packageName\": \"com.whatsapp\",\n");
                jsonBuilder.append("      \"installType\": \"FORCE_INSTALLED\"\n");
                jsonBuilder.append("    }\n");
                jsonBuilder.append("  ]\n");
                break;
                
            case "BLOCK_APP":
                jsonBuilder.append("  \"applications\": [\n");
                jsonBuilder.append("    {\n");
                jsonBuilder.append("      \"packageName\": \"com.whatsapp\",\n");
                jsonBuilder.append("      \"installType\": \"BLOCKED\"\n");
                jsonBuilder.append("    }\n");
                jsonBuilder.append("  ]\n");
                break;

            case "DISABLE_CAMERA":
                jsonBuilder.append("  \"cameraDisabled\": true\n");
                break;

            case "LOCK_DEVICE":
                jsonBuilder.append("  \"maximumTimeToLock\": \"0s\"\n");
                break;

            default:
                throw new IllegalArgumentException("Unsupported command: " + command);
        }
        
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }
}
