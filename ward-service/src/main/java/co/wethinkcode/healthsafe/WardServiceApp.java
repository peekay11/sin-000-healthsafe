package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

public class WardServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", DataRetrieval::wards);
        app.get("/wards/{id}", DataRetrieval::ward);
        app.get("/ward/{id}", DataRetrieval::ward);
        app.get("/departments", DataRetrieval::departments);
    }
}
