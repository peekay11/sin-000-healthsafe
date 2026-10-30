package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertLevelServiceApp {

    private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 8;
    private static final AtomicInteger LEVEL = new AtomicInteger(MIN_LEVEL);

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7032);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alert-level", ctx -> ctx.json(Map.of("level", LEVEL.get())));
        app.put("/alert-level", AlertLevelServiceApp::updateLevel);
        app.post("/alert-level", AlertLevelServiceApp::updateLevel);
    }

    private static void updateLevel(Context ctx) {
        AlertLevelRequest request;
        try {
            request = ctx.bodyAsClass(AlertLevelRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "body must be {\"level\": 0-8}"));
            return;
        }

        Integer level = request == null ? null : request.level();
        if (level == null || level < MIN_LEVEL || level > MAX_LEVEL) {
            ctx.status(400).json(Map.of("error", "level must be between 0 and 8"));
            return;
        }

        LEVEL.set(level);
        ctx.json(Map.of("level", level));
    }

    public record AlertLevelRequest(Integer level) {
    }
}
