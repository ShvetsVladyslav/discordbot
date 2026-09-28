package org.baddie.discordbot;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class SheetsWriter {

    private final Sheets sheets;
    private final String spreadsheetId;
    private final String sheetName;

    public SheetsWriter(
            @Value("${service.spreadsheet.table-id}") String spreadsheetId,
            @Value("${service.spreadsheet.sheet-name}") String sheetName,
            @Value("${service.spreadsheet.key-file-name:table-key.json}") String serviceAccountResource,
            @Value("${service.spreadsheet.key-file-path:}") String serviceAccountPath,
            @Value("${service.spreadsheet.key-file-base64:}") String serviceAccountBase64
    ) throws Exception {
        this.spreadsheetId = spreadsheetId;
        this.sheetName = sheetName;

        try (InputStream in = serviceAccountInputStream(serviceAccountResource, serviceAccountPath, serviceAccountBase64)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(List.of(SheetsScopes.SPREADSHEETS));

            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

            this.sheets = new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    requestInitializer
            ).setApplicationName("Discord Tournament Bot").build();
        }
    }

    private InputStream serviceAccountInputStream(String resourceName, String filePath, String base64Json) throws Exception {
        if (hasText(base64Json)) {
            byte[] decoded = Base64.getDecoder().decode(base64Json.trim());
            return new ByteArrayInputStream(decoded);
        }

        if (hasText(filePath)) {
            return Files.newInputStream(Path.of(filePath));
        }

        ClassPathResource resource = new ClassPathResource(resourceName);
        return resource.getInputStream();
    }

    public void appendRow(List<Object> row) throws Exception {
        ValueRange body = new ValueRange().setValues(List.of(row));
        String range = sheetName + "!A1";

        sheets.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }

    public List<List<Object>> readRows(String sheetName, String range) throws Exception {
        return readRows(spreadsheetId, sheetName, range);
    }

    public List<List<Object>> readRows(String spreadsheetId, String sheetName, String range) throws Exception {
        String sheetRange = formatSheetRange(sheetName, range);
        ValueRange response = sheets.spreadsheets().values()
                .get(spreadsheetId, sheetRange)
                .execute();

        List<List<Object>> values = response.getValues();
        return values == null ? List.of() : values;
    }

    private String formatSheetRange(String sheetName, String range) {
        String escapedSheetName = sheetName.replace("'", "''");
        return "'" + escapedSheetName + "'!" + range;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
