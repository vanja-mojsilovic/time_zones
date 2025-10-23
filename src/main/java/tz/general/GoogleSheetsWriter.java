package tz.general;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.*;
import java.util.*;
import java.security.GeneralSecurityException;

public class GoogleSheetsWriter {

    private static final String APPLICATION_NAME = "SpotHopper Integration";
    private static final String SPREADSHEET_ID = "1PD29O3JbMcfovGBIsXTcJKF5Rj1uRn7GSgoloHEdjbk";
    private static final String SHEET_NAME = "result_sheet";

    public static void writeSpotsToSheet(List<Map<String, Object>> spots) throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();

        List<List<Object>> rows = new ArrayList<>();
        List<Object> header = List.of("id", "name", "hours_of_operation", "time_zone", "zip", "latitude", "longitude", "metro_area_id", "metro_area_name");
        rows.add(header);

        for (Map<String, Object> spot : spots) {
            List<Object> row = new ArrayList<>();
            for (Object key : header) {
                row.add(spot.getOrDefault(key, ""));
            }
            rows.add(row);
        }

        ValueRange body = new ValueRange().setValues(rows);
        String range = SHEET_NAME + "!C1";
        service.spreadsheets().values()
                .update(SPREADSHEET_ID, range, body)
                .setValueInputOption("RAW")
                .execute();
    }

    private static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        GoogleCredentials credentials;

        File localCredentials = new File("credentials.json");
        if (localCredentials.exists()) {
            try (FileInputStream serviceAccountStream = new FileInputStream(localCredentials)) {
                credentials = GoogleCredentials.fromStream(serviceAccountStream)
                        .createScoped(List.of(SheetsScopes.SPREADSHEETS));
            }
        } else {
            String base64 = System.getenv("GOOGLE_CREDENTIALS_BASE64");
            if (base64 == null || base64.isEmpty()) {
                throw new IOException("Missing GOOGLE_CREDENTIALS_BASE64 environment variable for remote authentication.");
            }

            byte[] decoded = Base64.getDecoder().decode(base64);
            try (InputStream stream = new ByteArrayInputStream(decoded)) {
                credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(List.of(SheetsScopes.SPREADSHEETS));
            }
        }

        return new Sheets.Builder(
                com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
                com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
