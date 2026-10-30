package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;

public class WardServiceApp {

    public static void main(String[] args) {
        MqConfig.connect();
        MqConfig.subscribeTopic();
        JsonReader.publishSampleFailures();

        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", DataRetrieval::wards);
        app.get("/wards/{id}", DataRetrieval::ward);
        app.get("/ward/{id}", DataRetrieval::ward);
        app.get("/departments", DataRetrieval::departments);
        app.get("/staffing-updates", ctx -> {
            String event = MqConfig.lastStaffingEvent();
            if (event == null) {
                ctx.status(204);
                return;
            }
            ctx.contentType("application/json").result(event);
        });
        app.post("/equipment-failure", ctx -> {
            EquipmentFailurePayload payload = ctx.bodyAsClass(EquipmentFailurePayload.class);
            MqConfig.publishEquipmentFailure(payload);
            ctx.status(202).json(payload);
        });
        app.events(event -> event.serverStopping(MqConfig::close));
    }
}
