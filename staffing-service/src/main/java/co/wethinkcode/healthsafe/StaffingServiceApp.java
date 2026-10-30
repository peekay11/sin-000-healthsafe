package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class StaffingServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7033);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/staffing/{wardId}", StaffingServiceApp::schedule);
        app.events(event -> event.serverStopping(MqConfig::close));
    }

    private static void schedule(Context ctx) {
        WardData.Result result = WardData.buildSchedule(ctx.pathParam("wardId"));
        if (result.schedule() != null) {
            ctx.json(result.schedule());
            return;
        }
        ctx.status(result.status()).json(Map.of("error", result.error()));
    }
}
