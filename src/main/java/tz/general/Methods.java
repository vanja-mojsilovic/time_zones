package tz.general;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Base64;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Methods {
    private static final ObjectMapper mapper = new ObjectMapper();

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

        int status = conn.getResponseCode();
        String response;
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
        if (allTasks == null || allTasks.isEmpty()) return "";
        if (suppressionList == null) suppressionList = Collections.emptyList();
        List<String> filtered = new ArrayList<>();
        for (String task : allTasks) {
            if (!suppressionList.contains(task)) {
                filtered.add(task);
            }
        }
        return String.join(",", filtered);
    }


    public static List<Map<String, Object>> fetchSpotsWithMetroNames(List<String> spotIdList, String email, String cookie) throws IOException {
        // Declare result list
        List<Map<String, Object>> allSpots = new ArrayList<>();
        // Iterate over input spot ID list
        for (String idStr : spotIdList) {
            int id;
            try {
                id = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                System.err.println("Skipping invalid spot ID: " + idStr);
                continue;
            }
            // Spothopper API request for spots table
            URL url = new URL("https://www.spothopperapp.com/api/spots/" + id);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("X-User-Email", email);
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                System.err.println("Failed to fetch spot ID " + id + " — HTTP " + status);
                continue;
            }
            try (InputStream is = conn.getInputStream()) {
                Map<String, Object> response = mapper.readValue(is, new TypeReference<>() {});
                Object spotsObj = response.get("spots");
                if (!(spotsObj instanceof List)) {
                    System.err.println("Unexpected spot structure for ID " + id);
                    continue;
                }
                List<Map<String, Object>> spots = (List<Map<String, Object>>) spotsObj;
                for (Map<String, Object> spot : spots) {
                    allSpots.add(cleanSpot(spot));
                }
            }
        }
        // Spothopper API request for metro_areas table
        URL metroUrl = new URL("https://www.spothopperapp.com/api/metro_areas");
        HttpURLConnection metroConn = (HttpURLConnection) metroUrl.openConnection();
        metroConn.setRequestMethod("GET");
        metroConn.setRequestProperty("Cookie", cookie);
        metroConn.setRequestProperty("X-User-Email", email);
        Map<Integer, String> metroMap;
        try (InputStream is = metroConn.getInputStream()) {
            Map<String, Object> response = mapper.readValue(is, new TypeReference<>() {});
            Object metroObj = response.get("metro_areas");
            if (!(metroObj instanceof List)) {
                System.err.println("Unexpected metro area structure");
                return allSpots;
            }
            List<Map<String, Object>> metroAreas = (List<Map<String, Object>>) metroObj;
            metroMap = new HashMap<>();
            for (Map<String, Object> area : metroAreas) {
                Object idObj = area.get("id");
                Object nameObj = area.get("name");
                if (idObj instanceof Integer) {
                    int id = (Integer) idObj;
                    String name = nameObj instanceof String ? (String) nameObj : "Unknown";
                    metroMap.put(id, name);
                }
            }

        }
        // Join spots and metro_areas
        for (Map<String, Object> spot : allSpots) {
            Object metroId = spot.get("metro_area_id");
            Integer metroKey = null;
            if (metroId instanceof Integer) {
                metroKey = (Integer) metroId;
            } else if (metroId instanceof String) {
                try {
                    metroKey = Integer.parseInt((String) metroId);
                } catch (NumberFormatException ignored) {}
            }
            if (metroKey == null) metroKey = -1;
            String metroName = metroMap.getOrDefault(metroKey, "N/A");
            spot.put("metro_area_name", metroName);
        }
        // return result
        return allSpots;
    }

    private static Map<String, Object> cleanSpot(Map<String, Object> spot) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        cleaned.put("id", spot.get("id"));
        cleaned.put("name", spot.get("name"));
        // Formatting working hours block
        Object rawHours = spot.get("hours_of_operation");
        StringBuilder formatted = new StringBuilder("{");
        if (rawHours instanceof List) {
            List<?> weeklyHours = (List<?>) rawHours;
            for (int i = 0; i < 7; i++) {
                if (i > 0) formatted.append(",");
                if (i < weeklyHours.size()) {
                    Object dayHours = weeklyHours.get(i);
                    if (dayHours instanceof List) {
                        List<?> range = (List<?>) dayHours;
                        if (range.size() == 2 && range.get(0) != null && range.get(1) != null) {
                            formatted.append("{")
                                    .append(toTimeString(range.get(0)))
                                    .append(",")
                                    .append(toTimeString(range.get(1)))
                                    .append("}");
                            continue;
                        }
                    }
                }
                formatted.append("{NULL,NULL}");
            }
        } else {
            formatted.append("{NULL,NULL},{NULL,NULL},{NULL,NULL},{NULL,NULL},{NULL,NULL},{NULL,NULL},{NULL,NULL}");
        }
        formatted.append("}");
        cleaned.put("hours_of_operation", formatted.toString());
        Object timeZone = spot.get("time_zone");
        cleaned.put("time_zone", timeZone != null ? timeZone : "NULL");
        Object zip = spot.get("zip");
        cleaned.put("zip", zip != null ? zip : "NULL");
        Object latitude = spot.get("latitude");
        cleaned.put("latitude", latitude != null ? latitude : "NULL");
        Object longitude = spot.get("longitude");
        cleaned.put("longitude", longitude != null ? longitude : "NULL");
        Object metroAreaId = spot.get("metro_area_id");
        cleaned.put("metro_area_id", metroAreaId != null ? metroAreaId : "NULL");
        LocalDate currentDate = LocalDate.now();
        String formattedDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        cleaned.put("date", formattedDate != null ? formattedDate : "NULL");
        return cleaned;
    }

    private static String toTimeString(Object value) {
        String raw = value.toString().trim();
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
            LocalTime time = LocalTime.parse(raw, formatter);
            return time.toString();
        } catch (DateTimeParseException e) {
            System.err.println("Invalid time format: " + raw);
            return "NULL";
        }
    }
}
