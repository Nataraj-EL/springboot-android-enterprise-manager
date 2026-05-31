package com.example.androidmanagementpoc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class AndroidManagementService {

    @Autowired
    private GoogleAuthService googleAuthService;

    // Hardcoded per requirements
    // PLEASE REPLACE THIS WITH YOUR ACTUAL ENTERPRISE ID
    private static final String ENTERPRISE_ID = "LC024ke615";
    private static final String POLICY_ID = "default";
    
    private static final String API_URL = "https://androidmanagement.googleapis.com/v1/enterprises/" 
            + ENTERPRISE_ID + "/policies/" + POLICY_ID;

    public void syncDefaultPolicy(String accessToken) throws Exception {
        System.out.println("\n=== Synchronizing Default Policy ===");
        // Synchronizing a default policy so device enrollment doesn't fail due to a missing policy resource
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
                .uri(URI.create(API_URL))
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

    public void createEnrollmentToken(String accessToken) throws Exception {
        System.out.println("\n=== Generating Enrollment Token ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises/" + ENTERPRISE_ID + "/enrollmentTokens";
        
        // Pass policyName explicitly to bind the token to the synchronized default policy
        String jsonBody = "{\n" +
                "  \"policyName\": \"enterprises/" + ENTERPRISE_ID + "/policies/" + POLICY_ID + "\"\n" +
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

        // Extract and write token and QR code files
        writeEnrollmentFiles(response.body());

        System.out.println("============================\n");
    }

    private void writeEnrollmentFiles(String responseBody) {
        try {
            // Extract value
            java.util.regex.Matcher valueMatcher = java.util.regex.Pattern.compile("\"value\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(responseBody);
            if (valueMatcher.find()) {
                String tokenValue = valueMatcher.group(1);
                java.nio.file.Files.writeString(java.nio.file.Paths.get("enrollment-token.txt"), tokenValue);
                System.out.println("Generated enrollment-token.txt at: " + java.nio.file.Paths.get("enrollment-token.txt").toAbsolutePath());
            }
            
            // Extract qrCode JSON payload
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

    public void createSignupUrl(String accessToken, String projectId) throws Exception {
        System.out.println("\n=== Generating Signup URL ===");
        // The API requires the projectId parameter to tie the enterprise to your Google Cloud Project
        String url = "https://androidmanagement.googleapis.com/v1/signupUrls?projectId=" + projectId;
        
        // The API requires a valid HTTPS callbackUrl or it throws INSECURE_CALLBACK_URL 
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

    public void listDevices(String accessToken) throws Exception {
        System.out.println("\n=== Listing Devices ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises/" + ENTERPRISE_ID + "/devices";

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

    public void listPolicies(String accessToken) throws Exception {
        System.out.println("\n=== Listing Policies ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises/" + ENTERPRISE_ID + "/policies";

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

    public void listEnrollmentTokens(String accessToken) throws Exception {
        System.out.println("\n=== Listing Enrollment Tokens ===");
        String url = "https://androidmanagement.googleapis.com/v1/enterprises/" + ENTERPRISE_ID + "/enrollmentTokens";

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

    public void executeCommand(String command) throws Exception {
        /*
         * DEVICE REQUIREMENT:
         * 1. Device must be enrolled using Android Device Policy app
         * 2. Enrollment via QR code linking to this enterprise
         * 3. Without enrollment, policies and commands will not be applied to the device
         */
         
        String policyJson = generatePolicyJson(command);
        System.out.println("Generated Policy JSON:\n" + policyJson);

        String accessToken = googleAuthService.getAccessToken();
        
        // Using Java 11 natively built-in HttpClient to make PATCH request
        HttpClient client = HttpClient.newHttpClient();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(policyJson))
                .build();
                
        System.out.println("Sending policy to Android Management API Endpoint...");
        System.out.println("Endpoint: " + API_URL);
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("API Response Status: " + response.statusCode());
        System.out.println("API Response Body: " + response.body());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("API Request failed with status code: " + response.statusCode());
        }
    }

    private String generatePolicyJson(String command) {
        // Dynamically generating policy JSON based on the command
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");

        switch (command.toUpperCase()) {
            case "INSTALL_APP":
                // Install WhatsApp (com.whatsapp)
                jsonBuilder.append("  \"applications\": [\n");
                jsonBuilder.append("    {\n");
                jsonBuilder.append("      \"packageName\": \"com.whatsapp\",\n");
                jsonBuilder.append("      \"installType\": \"FORCE_INSTALLED\"\n");
                jsonBuilder.append("    }\n");
                jsonBuilder.append("  ]\n");
                break;
                
            case "BLOCK_APP":
                // Block WhatsApp (com.whatsapp)
                jsonBuilder.append("  \"applications\": [\n");
                jsonBuilder.append("    {\n");
                jsonBuilder.append("      \"packageName\": \"com.whatsapp\",\n");
                jsonBuilder.append("      \"installType\": \"BLOCKED\"\n");
                jsonBuilder.append("    }\n");
                jsonBuilder.append("  ]\n");
                break;

            case "DISABLE_CAMERA":
                // Disable camera globally
                jsonBuilder.append("  \"cameraDisabled\": true\n");
                break;

            case "LOCK_DEVICE":
                // Force Immediate Lock: Sets the maximum time to lock to 0 seconds.
                jsonBuilder.append("  \"maximumTimeToLock\": \"0s\"\n");
                break;

            default:
                throw new IllegalArgumentException("Unsupported command: " + command);
        }
        
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }
}
