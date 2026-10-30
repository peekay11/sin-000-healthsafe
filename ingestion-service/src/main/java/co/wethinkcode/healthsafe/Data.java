package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import io.javalin.http.Context;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Data {

    private static final Set<String> PLACEHOLDERS =
            Set.of("", "-", "n/a", "na", "tbd", "null", "none", "nan", "unknown");

    private Data() {
    }

    public static void readCsv(Context ctx) {
        InputStream input = IngestionServiceApp.class.getClassLoader()
                .getResourceAsStream("wards-outdated.csv");
        if (input == null) {
            ctx.status(500).json(Map.of("error", "wards-outdated.csv not found"));
            return;
        }

        try (CSVReader reader = new CSVReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                ctx.status(500).json(Map.of("error", "wards-outdated.csv is empty"));
                return;
            }
            ctx.json(clean(rows));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Could not clean wards data"));
        }
    }

    private static List<Map<String, String>> clean(List<String[]> rows) {
        String[] header = Arrays.stream(rows.get(0))
                .map(Data::normaliseText)
                .toArray(String[]::new);

        List<Map<String, String>> wards = new ArrayList<>();
        Set<String> seenWardIds = new HashSet<>();

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            Map<String, String> raw = new HashMap<>();
            for (int column = 0; column < header.length && column < row.length; column++) {
                raw.put(header[column], row[column]);
            }

            String wardId = normaliseText(raw.get("ward_id"));
            if (wardId == null || !seenWardIds.add(wardId)) {
                continue;
            }

            Map<String, String> ward = new LinkedHashMap<>();
            ward.put("ward_id", wardId);
            ward.put("wing", normaliseText(raw.get("wing")));
            ward.put("department", normaliseText(raw.get("department")));
            ward.put("beds_available", cleanBeds(raw.get("beds_available")));
            wards.add(ward);
        }

        return wards;
    }

    private static String normaliseText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return PLACEHOLDERS.contains(cleaned) ? null : cleaned;
    }

    private static String cleanBeds(String value) {
        String cleaned = normaliseText(value);
        if (cleaned == null) {
            return null;
        }
        try {
            int beds = Integer.parseInt(cleaned);
            return beds < 0 ? null : Integer.toString(beds);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
