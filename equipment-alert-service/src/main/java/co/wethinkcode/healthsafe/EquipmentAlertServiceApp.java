package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;

public class EquipmentAlertServiceApp {

    public static void main(String[] args) {
        MqConfig.connect();
        MqConfig.listenToQueue();

        Javalin app = Javalin.create().start(7034);

        app.get("/health", ctx -> ctx.result("OK"));
        app.events(event -> event.serverStopping(MqConfig::close));
    }
}
