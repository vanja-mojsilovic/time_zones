package tz.general;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.security.GeneralSecurityException;
import tz.general.GoogleSheetsWriter;

public class MainClass {
    public static void main(String[] args) {
        // Switch blocks on and off
        boolean addNewRecords = true;
        boolean archiveCurrentRecords = true;

        // Setup constants and variables
        String email;
        String jiraApiToken;
        String cookie;
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
            email = dotenv.get("EMAIL", System.getenv("EMAIL"));
            jiraApiToken = dotenv.get("JIRA_API_KEY", System.getenv("JIRA_API_KEY"));
            cookie = dotenv.get("SPOTHOPPER_COOKIES", System.getenv("SPOTHOPPER_COOKIES"));
        } catch (Exception e) {
            System.err.println("Warning: Failed to load .env file. Falling back to system environment variables.");
            email = System.getenv("EMAIL");
            jiraApiToken = System.getenv("JIRA_API_KEY");
            cookie = System.getenv("SPOTHOPPER_COOKIES");
        }
        if (email == null || jiraApiToken == null) {
            System.err.println("Missing EMAIL or JIRA_API_KEY. Please set them in .env or as environment variables.");
            return;
        }
        // Jira API request
        if(addNewRecords){
            String jql = "summary ~ \"Go Live\" AND status = QA";
            List<String> suppressionList = List.of("");
            try {
                List<String> spotIdList = Methods.fetchSpotIdList(email, jiraApiToken, jql);
        // Spothopper API request
                String filteredCsv = Methods.getFilteredTasksCsv(spotIdList, suppressionList);
                System.out.println("Filtered CSV: " + filteredCsv);
                List<Map<String, Object>> metroResults = Methods.fetchSpotsWithMetroNames(spotIdList, email, cookie);
                System.out.println("Metro-mapped spots:");
                for (Map<String, Object> spot : metroResults) {
                    System.out.println("Spot details:");
                    for (Map.Entry<String, Object> entry : spot.entrySet()) {
                        System.out.println("  " + entry.getKey() + ": " + entry.getValue());
                    }
                    System.out.println();
                }
        // Google sheets service
                GoogleSheetsWriter.writeSpotsToSheet(metroResults);
                System.out.println("Spot data successfully written to Google Sheets.");
            } catch (Exception e) {
                System.err.println("Error: " + (e != null ? e.getMessage() : "Unknown exception"));
            }
        }
        // Archive current records
        if(archiveCurrentRecords){
            String sourceRange = "current_list!A1:L100";
            String destinationRange = "archive_2025!A1:L";
            try {
                GoogleSheetsWriter.ArchiveDailyTasks(sourceRange, destinationRange);
                System.out.println("Data successfully archived from " + sourceRange + " to " + destinationRange);
            } catch (Exception e) {
                System.err.println("Archiving failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown exception"));
                e.printStackTrace();
            }
        }


    }
}
