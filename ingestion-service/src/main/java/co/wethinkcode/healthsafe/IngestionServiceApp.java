package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

public class IngestionServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", Data::readCsv);
    }
}
