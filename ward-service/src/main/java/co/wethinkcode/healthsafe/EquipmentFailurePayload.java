package co.wethinkcode.healthsafe;

import java.time.Instant;

public record EquipmentFailurePayload(
        String wardId,
        String equipment,
        String severity,
        String time
) {
    public EquipmentFailurePayload {
        if (time == null || time.isBlank()) {
            time = Instant.now().toString();
        }
    }
}
