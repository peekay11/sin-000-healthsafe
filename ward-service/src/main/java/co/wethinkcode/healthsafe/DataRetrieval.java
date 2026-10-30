package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public final class DataRetrieval {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final String INGESTION_URL = "http://localhost:7030/wards";

    private DataRetrieval() {
    }

    public static void wards(Context ctx) {
        JsonNode[] wards = fetchWards();
        if (wards == null) {
            ctx.status(503).json(Map.of("error", "ingestion-service unavailable"));
            return;
        }
        ctx.json(wards);
    }

    public static void ward(Context ctx) {
        JsonNode[] wards = fetchWards();
        if (wards == null) {
            ctx.status(503).json(Map.of("error", "ingestion-service unavailable"));
            return;
        }

        String id = ctx.pathParam("id");
        for (JsonNode ward : wards) {
            JsonNode wardId = ward.get("ward_id");
            if (wardId != null && id.equalsIgnoreCase(wardId.asText())) {
                ctx.json(ward);
                return;
            }
        }
        ctx.status(404).json(Map.of("error", "ward not found"));
    }

    public static void departments(Context ctx) {
        JsonNode[] wards = fetchWards();
        if (wards == null) {
            ctx.status(503).json(Map.of("error", "ingestion-service unavailable"));
            return;
        }

        Set<String> departments = new TreeSet<>();
        for (JsonNode ward : wards) {
            JsonNode department = ward.get("department");
            if (department != null && !department.isNull()) {
                departments.add(department.asText());
            }
        }
        ctx.json(departments);
    }

    private static JsonNode[] fetchWards() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(INGESTION_URL))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            return JSON.readValue(response.body(), JsonNode[].class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
