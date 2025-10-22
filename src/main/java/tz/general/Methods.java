package tz.general;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Base64;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Methods {

    public static List<String> fetchSpotIdList(String email, String apiToken, String jql) throws Exception {
        String apiUrl = "https://spothopper.atlassian.net/rest/api/3/search/jql";
        String jqlPayload = String.format("{\"jql\": \"%s\", \"fields\": [\"key\", \"customfield_10053\"]}", jql.replace("\"", "\\\""));
        String auth = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Basic " + auth);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jqlPayload.getBytes(StandardCharsets.UTF_8));
        }
        String response;
        int status = conn.getResponseCode();
        if (status >= 200 && status < 300) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                response = reader.lines().collect(Collectors.joining("\n"));
            }
        } else {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String errorResponse = errorReader.lines().collect(Collectors.joining("\n"));
                throw new IOException("Request failed with status " + status + ": " + errorResponse);
            }
        }
        JSONObject json = new JSONObject(response);
        JSONArray issues = json.getJSONArray("issues");
        List<String> spotIds = new ArrayList<>();
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.getJSONObject(i);
            JSONObject fields = issue.getJSONObject("fields");
            String spotId = fields.optString("customfield_10053", null);
            if (spotId != null && !spotId.isEmpty()) {
                spotIds.add(spotId);
            }
        }
        return spotIds;
    }


    public static String getFilteredTasksCsv(List<String> allTasks, List<String> suppressionList) {
        if (allTasks == null || allTasks.isEmpty()) {
            return "";
        }
        if (suppressionList == null) {
            suppressionList = Collections.emptyList();
        }

        List<String> filtered = new ArrayList<>();
        for (String task : allTasks) {
            if (!suppressionList.contains(task)) {
                filtered.add(task);
            }
        }

        return String.join(",", filtered);
    }
}
