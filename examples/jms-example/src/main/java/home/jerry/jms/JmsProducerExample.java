package home.jerry.jms;

import javax.jms.*;
import javax.naming.InitialContext;

public class JmsProducerExample {
    public static void main(String[] args) throws Exception {

        Connection connection = null;
        InitialContext initialContext = null;
        try {
            initialContext = new InitialContext();
            Queue queue = (Queue) initialContext.lookup("queue/test");
            ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup("ConnectionFactory");
            connection = connectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(queue);

            for (int i = 0; i < 100; i++) {
                TextMessage message = session.createTextMessage("Message " + i);
                System.out.println("Sending message: " + message.getText());
                producer.send(message);
            }

        } finally {
            if (initialContext != null) {
                initialContext.close();
            }
            if (connection != null) {
                connection.close();
            }
        }


    }
}
