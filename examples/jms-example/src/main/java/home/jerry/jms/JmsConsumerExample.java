package home.jerry.jms;

import javax.jms.*;
import javax.naming.InitialContext;

public class JmsConsumerExample {
    public static void main(String[] args) throws Exception {
        Connection connection = null;
        InitialContext initialContext = null;
        MessageConsumer consumer = null;
        try {
            initialContext = new InitialContext();
            Queue queue = (Queue) initialContext.lookup("queue/test");
            ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup("ConnectionFactory");
            connection = connectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            consumer = session.createConsumer(queue);
            consumer.setMessageListener(message -> {
                try {
                    Thread.sleep(5000L);
                    System.out.printf("Received message - Id %s Message %s %n", message.getJMSMessageID(), ((TextMessage)message).getText());
                } catch (Exception e) {
                    System.out.printf("Error: %s%n", e.getMessage());
                }
            });
            connection.start();

            while (true) {
                // Perform other tasks or wait for messages
                Thread.sleep(1000L);
            }
        } finally {
            if (initialContext != null) {
                initialContext.close();
            }
            if (connection != null) {
                connection.close();
            }
            if (consumer != null) {
                consumer.close();
            }
        }


    }
}
