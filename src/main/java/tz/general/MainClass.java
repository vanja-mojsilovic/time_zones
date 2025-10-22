package tz.general;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;

public class MainClass {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();

        String email = dotenv.get("EMAIL");
        String apiToken = dotenv.get("JIRA_API_KEY");

        // JQL filter
        String jql = "summary ~ \"Go Live\" AND status = QA";

        //  suppression list
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
