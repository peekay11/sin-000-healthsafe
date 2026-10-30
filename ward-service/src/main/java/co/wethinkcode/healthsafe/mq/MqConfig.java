package co.wethinkcode.healthsafe.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * Shared by every producer/consumer service that talks to the ActiveMQ broker.
 * Duplicated into each participating service's own source tree, since these are
 * independent Maven projects with no shared parent pom.
 *
 * ward-service is a consumer of TOPIC (staffing updates) and a producer on QUEUE
 * (equipment failures detected on its wards).
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String TOPIC = "staffing-events-topic";

    private static final ConnectionFactory CONNECTION_FACTORY = new ActiveMQConnectionFactory(BROKER_URL);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static Connection connection;
    private static Session session;
    private static volatile String lastStaffingEvent;

    private MqConfig() {
    }

    public static synchronized void connect() {
        if (session != null) {
            return;
        }
        try {
            connection = CONNECTION_FACTORY.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            System.out.println("Could not connect to ActiveMQ: " + e.getMessage());
        }
    }

    public static void subscribeTopic() {
        if (session == null) {
            System.out.println("No broker connection, skipping " + TOPIC + " subscription");
            return;
        }

        try {
            MessageConsumer consumer = session.createConsumer(session.createTopic(TOPIC));
            consumer.setMessageListener(MqConfig::handleMessage);
            System.out.println("Subscribed to " + TOPIC);
        } catch (JMSException e) {
            System.out.println("Could not subscribe to " + TOPIC + ": " + e.getMessage());
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
            JsonNode event = JSON.readValue(payload, JsonNode.class);
            lastStaffingEvent = event.toString();
            System.out.println("Received staffing update for ward "
                    + event.path("wardId").asText("unknown"));
        } catch (Exception e) {
            System.out.println("Could not read staffing update: " + e.getMessage());
        }
    }

    public static String lastStaffingEvent() {
        return lastStaffingEvent;
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
