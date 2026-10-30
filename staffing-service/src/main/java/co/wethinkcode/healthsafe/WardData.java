package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class WardData {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final int BASE_DOCTORS = 2;

    private WardData() {
    }

    public static Result buildSchedule(String wardId) {
        HttpResult wardResponse = get("http://localhost:7031/wards/" + encode(wardId));
        if (wardResponse == null) {
            return Result.error(503, "ward-service unavailable");
        }
        if (wardResponse.status() == 404) {
            return Result.error(404, "ward not found: " + wardId);
        }
        if (wardResponse.status() != 200) {
            return Result.error(502, "ward-service returned " + wardResponse.status());
        }

        JsonNode ward;
        try {
            ward = JSON.readValue(wardResponse.body(), JsonNode.class);
        } catch (Exception e) {
            return Result.error(502, "invalid response from ward-service");
        }

        JsonNode beds = ward.get("beds_available");
        if (beds == null || beds.isNull()) {
            return Result.error(409, "no beds available at ward " + wardId);
        }

        HttpResult alertResponse = get("http://localhost:7032/alert-level");
        if (alertResponse == null) {
            return Result.error(503, "alert-level-service unavailable");
        }
        if (alertResponse.status() != 200) {
            return Result.error(502, "alert-level-service returned " + alertResponse.status());
        }

        int alertLevel;
        try {
            JsonNode alert = JSON.readValue(alertResponse.body(), JsonNode.class);
            alertLevel = alert.path("level").asInt();
        } catch (Exception e) {
            return Result.error(502, "invalid response from alert-level-service");
        }

        List<Map<String, String>> doctors = readDoctors();
        if (doctors == null) {
            return Result.error(500, "could not read doctors.csv");
        }

        String department = ward.path("department").asText("");
        String[] chosen = chosenDoctors(neededDoctors(alertLevel), doctors, department);
        Schedule schedule = new Schedule(chosen, alertLevel, department, ward.path("ward_id").asText(wardId));
        return Result.ok(schedule);
    }

    private static HttpResult get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static int neededDoctors(int alertLevel) {
        return BASE_DOCTORS + alertLevel;
    }

    private static String[] chosenDoctors(int count, List<Map<String, String>> doctors, String department) {
        List<String> matching = new ArrayList<>();
        for (Map<String, String> doctor : doctors) {
            if (doctor.get("department").equalsIgnoreCase(department)) {
                matching.add(doctor.get("full_name"));
            }
        }

        Collections.shuffle(matching);
        return matching.subList(0, Math.min(count, matching.size())).toArray(String[]::new);
    }

    private static List<Map<String, String>> readDoctors() {
        InputStream input = StaffingServiceApp.class.getClassLoader()
                .getResourceAsStream("doctors.csv");
        if (input == null) {
            return null;
        }

        try (CSVReader reader = new CSVReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                return List.of();
            }

            String[] header = rows.get(0);
            List<Map<String, String>> doctors = new ArrayList<>();
            for (int row = 1; row < rows.size(); row++) {
                Map<String, String> doctor = new HashMap<>();
                for (int column = 0; column < header.length && column < rows.get(row).length; column++) {
                    doctor.put(header[column].trim().toLowerCase(Locale.ROOT), rows.get(row)[column].trim());
                }
                doctors.add(doctor);
            }
            return doctors;
        } catch (Exception e) {
            return null;
        }
    }

    private record HttpResult(int status, String body) {
    }

    public record Result(int status, Schedule schedule, String error) {
        static Result ok(Schedule schedule) {
            return new Result(200, schedule, null);
        }

        static Result error(int status, String message) {
            return new Result(status, null, message);
        }
    }
}
