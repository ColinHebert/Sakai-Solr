package uk.ac.ox.oucs.search.queueing;

import com.rabbitmq.client.*;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ox.oucs.search.indexing.Task;
import uk.ac.ox.oucs.search.indexing.TaskHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.ExecutorService;

/**
 * @author Colin Hebert
 */
public class AmqpHandler {
    private static final Logger logger = LoggerFactory.getLogger(AmqpQueueing.class);
    private TaskHandler taskHandler;
    private SessionManager sessionManager;
    private ConnectionFactory connectionFactory;
    private ExecutorService executor;
    private boolean running = true;
    private Connection amqpConnection;
    private Channel channel;
    private String queueName;

    public void init() {
        try {
            amqpConnection = connectionFactory.newConnection(executor);
            amqpConnection.addShutdownListener(new AmqpHandlerShutdownListener());
            channel = amqpConnection.createChannel();
            channel.basicConsume(queueName, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag,
                                           Envelope envelope,
                                           AMQP.BasicProperties properties,
                                           byte[] body)
                        throws IOException {
                    long deliveryTag = envelope.getDeliveryTag();
                    handleMessage(deserialize(body));
                    channel.basicAck(deliveryTag, false);
                }
            });
        } catch (IOException e) {
            logger.error("Exception while trying to get tasks from the AMQP server", e);
        }
    }

    public void destroy() {
        synchronized (this) {
            try {
                running = false;
                channel.close();
                amqpConnection.close();
            } catch (IOException e) {
                logger.error("Exception while closing the connection to the AMQP server", e);
            }
        }
    }

    public void handleMessage(Task task) {
        if (task == null)
            logger.error("Null task, ignored");

        Session session = sessionManager.getCurrentSession();
        session.setUserId("admin");
        session.setUserEid("admin");

        taskHandler.executeTask(task);
    }

    private Task deserialize(byte[] message) {
        ObjectInputStream ois = null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(message);
            ois = new ObjectInputStream(bais);
            return (Task) ois.readObject();
        } catch (Exception e) {
            logger.error("Couldn't deserialize the content", e);
        } finally {
            try {
                if (ois != null)
                    ois.close();
            } catch (IOException e) {
                logger.error("Couldn't close the ObjectInputStream", e);
            }
        }

        return null;
    }

    public void setTaskHandler(TaskHandler taskHandler) {
        this.taskHandler = taskHandler;
    }

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    private class AmqpHandlerShutdownListener implements ShutdownListener {

        @Override
        public void shutdownCompleted(ShutdownSignalException cause) {
            synchronized (this) {
                //TODO: Avoid looping reconnection?
                if (running)
                    init();
            }
        }
    }
}
