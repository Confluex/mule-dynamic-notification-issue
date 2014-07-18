package com.confluex.mule.bugs.notifications;

import static org.junit.Assert.*;

import com.confluex.mule.test.BeforeMule;
import com.confluex.mule.test.BetterFunctionalTestCase;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.api.MuleContext;
import org.mule.api.context.notification.ServerNotification;
import org.mule.api.context.notification.ServerNotificationListener;
import org.mule.context.notification.EndpointMessageNotification;
import org.mule.context.notification.MessageProcessorNotification;
import org.mule.context.notification.TransactionNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsTemplate;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.util.concurrent.atomic.AtomicInteger;

public class DynamicNotificationsTest extends BetterFunctionalTestCase {

    Logger log = LoggerFactory.getLogger(getClass());

    private ActiveMQConnectionFactory amqConnectionFactory;

    private JmsTemplate jms;
    private JmsTemplate jmsAdmin;
    private Connection keepaliveConnection;

    @Override
    public String getConfigFile() {
        return "notification-issue.xml";
    }

    @BeforeMule
    public void initJms(MuleContext muleContext) throws JMSException {
        amqConnectionFactory = muleContext.getRegistry().lookupObject("amqConnectionFactory");

        UserCredentialsConnectionFactoryAdapter adminConnectionFactory = new UserCredentialsConnectionFactoryAdapter();
        log.debug("Setting up admin connection factory " + amqConnectionFactory.getBrokerURL());
        adminConnectionFactory.setTargetConnectionFactory(amqConnectionFactory);
        adminConnectionFactory.setUsername("god");
        adminConnectionFactory.setPassword("password");

        jmsAdmin = new JmsTemplate(adminConnectionFactory);
        jmsAdmin.setReceiveTimeout(100);

        jms = new JmsTemplate(amqConnectionFactory);
        jms.setReceiveTimeout(5000);
        keepaliveConnection = amqConnectionFactory.createConnection();

        createQueue("input");
        createQueue("DLQ.input");
        createQueue("output");
    }

    @After
    public void stopKeepaliveConnections() throws JMSException {
        keepaliveConnection.close();
    }

    @Test
    public void endpointNotificationShouldBeDetectedWithinThreeSeconds() throws Exception {
        CountingListener<EndpointMessageNotification> endpointNotificationListener = new CountingListener<EndpointMessageNotification>();
        muleContext.registerListener(endpointNotificationListener);

        jms.convertAndSend("input", "test data");

        Thread.sleep(3000);

        assertTrue(endpointNotificationListener.getNotificationCount() > 0);
    }

    @Test
    public void messageProcessorNotificationShouldBeDetectedWithinThreeSeconds() throws Exception {
        CountingListener<MessageProcessorNotification> messageProcessorListener = new CountingListener<MessageProcessorNotification>();
        muleContext.registerListener(messageProcessorListener);

        jms.convertAndSend("input", "test data");

        Thread.sleep(3000);

        assertTrue(messageProcessorListener.getNotificationCount() > 0);
    }

    @Test
    public void transactionNotificationShouldBeDetectedWithingThreeSeconds() throws Exception {
        CountingListener<TransactionNotification> transactionListener = new CountingListener<TransactionNotification>();
        muleContext.registerListener(transactionListener);

        jms.convertAndSend("input", "test data");

        Thread.sleep(3000);

        assertTrue(transactionListener.getNotificationCount() > 0);
    }

    private void createQueue(String queue) {
        jmsAdmin.receive(queue);
    }

    public static class CountingListener<T extends ServerNotification> implements ServerNotificationListener<T> {
        private AtomicInteger notificationCount = new AtomicInteger(0);
        private Logger log = LoggerFactory.getLogger(getClass());

        @Override
        public void onNotification(T notification) {
            log.debug("Received notification {}", notification.getClass().getName());
            notificationCount.incrementAndGet();
        }

        public int getNotificationCount() {
            return notificationCount.get();
        }
    }

}
