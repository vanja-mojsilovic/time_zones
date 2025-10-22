package tz.general;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;

public class MainClass {
    public static void main(String[] args) {
        String email;
        String apiToken;

        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();

            email = dotenv.get("EMAIL", System.getenv("EMAIL"));
            apiToken = dotenv.get("JIRA_API_KEY", System.getenv("JIRA_API_KEY"));
        } catch (Exception e) {
            System.err.println("Warning: Failed to load .env file. Falling back to system environment variables.");
            email = System.getenv("EMAIL");
            apiToken = System.getenv("JIRA_API_KEY");
        }

        if (email == null || apiToken == null) {
            System.err.println("Missing EMAIL or JIRA_API_KEY. Please set them in .env or as environment variables.");
            return;
        }

        String jql = "summary ~ \"Go Live\" AND status = QA";
        List<String> suppressionList = List.of("");

        try {
            List<String> issueKeys = Methods.fetchSpotIdList(email, apiToken, jql);
            String filteredCsv = Methods.getFilteredTasksCsv(issueKeys, suppressionList);
            System.out.println("Filtered CSV: " + filteredCsv);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
