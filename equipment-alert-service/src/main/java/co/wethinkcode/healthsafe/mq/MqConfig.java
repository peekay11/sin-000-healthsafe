package co.wethinkcode.healthsafe.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * Shared by every producer/consumer service that talks to the "equipment-failure-queue"
 * ActiveMQ queue. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 *
 * Messages are sent with PERSISTENT delivery and acknowledged only after they have
 * been processed, so a failure between receipt and handling leaves the alert on the
 * queue for redelivery.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String QUEUE = "equipment-failure-queue";

    private static final ConnectionFactory CONNECTION_FACTORY = new ActiveMQConnectionFactory(BROKER_URL);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static Connection connection;
    private static Session session;

    private MqConfig() {
    }

    public static synchronized void connect() {
        if (session != null) {
            return;
        }
        try {
            connection = CONNECTION_FACTORY.createConnection();
            connection.start();
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        } catch (JMSException e) {
            System.out.println("Could not connect to ActiveMQ: " + e.getMessage());
        }
    }

    public static void listenToQueue() {
        if (session == null) {
            System.out.println("No broker connection, equipment alerts will not be consumed");
            return;
        }

        try {
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE));
            consumer.setMessageListener(MqConfig::handleMessage);
            System.out.println("Listening on " + QUEUE);
        } catch (JMSException e) {
            System.out.println("Could not listen on " + QUEUE + ": " + e.getMessage());
        }
    }

    private static void handleMessage(Message message) {
        if (!(message instanceof TextMessage textMessage)) {
            return;
        }

        try {
            String payload = textMessage.getText();
            if (payload == null || payload.isBlank()) {
                return;
            }
            JsonNode alert = JSON.readValue(payload, JsonNode.class);
            System.out.println("EQUIPMENT ALERT: ward=" + alert.path("wardId").asText()
                    + " equipment=" + alert.path("equipment").asText()
                    + " severity=" + alert.path("severity").asText());
            message.acknowledge();
        } catch (Exception e) {
            System.out.println("Could not handle equipment alert: " + e.getMessage());
        }
    }

    public static synchronized void close() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (JMSException e) {
            System.out.println("Could not close ActiveMQ connection: " + e.getMessage());
        }
    }
}
