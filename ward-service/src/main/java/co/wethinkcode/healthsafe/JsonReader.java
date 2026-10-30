package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

public final class JsonReader {

    private static final String SAMPLE_FILE = "equipment_failure_payload.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonReader() {
    }

    public static void publishSampleFailures() {
        InputStream input = JsonReader.class.getClassLoader().getResourceAsStream(SAMPLE_FILE);
        if (input == null) {
            System.out.println("No file named " + SAMPLE_FILE + " found");
            return;
        }

        try {
            List<EquipmentFailurePayload> failures = JSON.readValue(input, new TypeReference<>() {
            });
            for (EquipmentFailurePayload failure : failures) {
                MqConfig.publishEquipmentFailure(failure);
            }
        } catch (Exception e) {
            System.out.println("Could not read " + SAMPLE_FILE + ": " + e.getMessage());
        }
    }
}
