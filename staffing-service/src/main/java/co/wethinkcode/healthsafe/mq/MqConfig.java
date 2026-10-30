package co.wethinkcode.healthsafe.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * Shared by every producer/consumer service that talks to the "staffing-events-topic"
 * ActiveMQ topic. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String TOPIC = "staffing-events-topic";

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
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            System.out.println("Could not connect to ActiveMQ: " + e.getMessage());
        }
    }

    public static void publishStaffEvents(Object event) {
        connect();
        if (session == null) {
            System.out.println("No broker connection, staffing update not published");
            return;
        }

        try (MessageProducer producer = session.createProducer(session.createTopic(TOPIC))) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            TextMessage message = session.createTextMessage(JSON.writeValueAsString(event));
            producer.send(message);
            System.out.println("Published staffing update to " + TOPIC);
        } catch (Exception e) {
            System.out.println("Could not publish staffing update: " + e.getMessage());
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
