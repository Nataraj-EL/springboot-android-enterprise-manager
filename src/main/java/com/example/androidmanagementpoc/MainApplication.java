package com.example.androidmanagementpoc;

import com.example.androidmanagementpoc.service.AndroidManagementService;
import com.example.androidmanagementpoc.service.GoogleAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MainApplication implements CommandLineRunner {

    @Autowired
    private AndroidManagementService androidManagementService;

    @Autowired
    private GoogleAuthService googleAuthService;

    public static void main(String[] args) {
        // This is a POC (Proof of Concept) application.
        // It runs completely without web server/REST controllers.
        SpringApplication.run(MainApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        
        java.io.PrintStream dualOut = new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                originalOut.write(b);
                baos.write(b);
            }
            @Override
            public void write(byte[] b, int off, int len) throws java.io.IOException {
                originalOut.write(b, off, len);
                baos.write(b, off, len);
            }
        });
        
        System.setOut(dualOut);

        System.out.println("\n==================================================");
        System.out.println("          ANDROID MANAGEMENT HEALTH CHECK");
        System.out.println("==================================================");

        boolean authPassed = false;
        String projectId = null;
        String authError = null;

        // 1. Authentication
        try {
            projectId = googleAuthService.getProjectId();
            authPassed = true;
            System.out.println("1. Authentication: PASS");
        } catch (Exception e) {
            authError = e.getMessage() != null ? e.getMessage() : e.toString();
            System.out.println("1. Authentication: FAIL");
            System.out.println("   ERROR DETAILS: " + authError);
        }

        // 2. Token retrieval
        boolean tokenPassed = false;
        String accessToken = null;
        if (authPassed) {
            try {
                accessToken = googleAuthService.getAccessToken();
                tokenPassed = true;
                System.out.println("2. Token retrieval: PASS");
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.toString();
                System.out.println("2. Token retrieval: FAIL");
                System.out.println("   ERROR DETAILS: " + error);
            }
        } else {
            System.out.println("2. Token retrieval: FAIL");
            System.out.println("   ERROR DETAILS: Skipped because Authentication failed");
        }

        // 3. Enterprise lookup
        boolean enterprisePassed = false;
        if (tokenPassed) {
            try {
                androidManagementService.listEnterprises(accessToken, projectId);
                enterprisePassed = true;
                System.out.println("3. Enterprise lookup: PASS");
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.toString();
                System.out.println("3. Enterprise lookup: FAIL");
                System.out.println("   ERROR DETAILS: " + error);
            }
        } else {
            System.out.println("3. Enterprise lookup: FAIL");
            System.out.println("   ERROR DETAILS: Skipped because Token retrieval failed");
        }

        // 4. Policy validation
        boolean policyPassed = false;
        if (tokenPassed) {
            try {
                androidManagementService.syncDefaultPolicy(accessToken);
                policyPassed = true;
                System.out.println("4. Policy validation: PASS");
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.toString();
                System.out.println("4. Policy validation: FAIL");
                System.out.println("   ERROR DETAILS: " + error);
            }
        } else {
            System.out.println("4. Policy validation: FAIL");
            System.out.println("   ERROR DETAILS: Skipped because Token retrieval failed");
        }

        // 5. Enrollment token generation
        boolean enrollmentPassed = false;
        if (tokenPassed && policyPassed) {
            try {
                androidManagementService.createEnrollmentToken(accessToken);
                enrollmentPassed = true;
                System.out.println("5. Enrollment token generation: PASS");
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.toString();
                System.out.println("5. Enrollment token generation: FAIL");
                System.out.println("   ERROR DETAILS: " + error);
            }
        } else {
            System.out.println("5. Enrollment token generation: FAIL");
            System.out.println("   ERROR DETAILS: Skipped because Token retrieval or Policy validation failed");
        }

        // 6. API connectivity
        if (tokenPassed) {
            System.out.println("6. API connectivity: PASS");
        } else {
            System.out.println("6. API connectivity: FAIL");
            System.out.println("   ERROR DETAILS: Skipped because Token retrieval failed");
        }

        // 7. Device listing
        if (tokenPassed) {
            try {
                androidManagementService.listDevices(accessToken);
                System.out.println("7. Device listing: PASS");
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.toString();
                System.out.println("7. Device listing: FAIL");
                System.out.println("   ERROR DETAILS: " + error);
            }
        } else {
            System.out.println("7. Device listing: FAIL");
            System.out.println("   ERROR DETAILS: Skipped because Token retrieval failed");
        }

        // 8. Policy listing
        if (tokenPassed) {
            try {
                androidManagementService.listPolicies(accessToken);
                System.out.println("8. Policy listing: PASS");
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.toString();
                System.out.println("8. Policy listing: FAIL");
                System.out.println("   ERROR DETAILS: " + error);
            }
        } else {
            System.out.println("8. Policy listing: FAIL");
            System.out.println("   ERROR DETAILS: Skipped because Token retrieval failed");
        }

        // 9. Enrollment Tokens listing
        if (tokenPassed) {
            try {
                androidManagementService.listEnrollmentTokens(accessToken);
                System.out.println("9. Enrollment Tokens listing: PASS");
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.toString();
                System.out.println("9. Enrollment Tokens listing: FAIL");
                System.out.println("   ERROR DETAILS: " + error);
            }
        } else {
            System.out.println("9. Enrollment Tokens listing: FAIL");
            System.out.println("   ERROR DETAILS: Skipped because Token retrieval failed");
        }

        System.out.println("==================================================\n");

        System.setOut(originalOut);

        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
            String timestamp = java.time.LocalDateTime.now().format(formatter);
            java.nio.file.Path logsDir = java.nio.file.Paths.get("logs");
            java.nio.file.Files.createDirectories(logsDir);
            java.nio.file.Path logFile = logsDir.resolve("health-check-" + timestamp + ".txt");
            java.nio.file.Files.writeString(logFile, baos.toString(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("Stored full health check output at: " + logFile.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to write log file: " + e.getMessage());
        }
    }
}
