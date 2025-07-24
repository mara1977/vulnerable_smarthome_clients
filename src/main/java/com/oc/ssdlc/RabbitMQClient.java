package com.oc.ssdlc;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RabbitMQClient {
    private static final String HOST = "localhost"; // Or the IP of your Docker host
    private static final int PORT = 5672;
    protected static final String EXCHANGE_NAME = "Smarthome";

    protected Connection connection;
    protected Channel channel;

    public RabbitMQClient() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        // NO AUTHENTICATION FOR DEMO PURPOSES!
        // In a real application, authentication MUST be configured!

        connection = factory.newConnection();
        channel = connection.createChannel();
        // Declare the topic exchange
        channel.exchangeDeclare(EXCHANGE_NAME, "topic", false); // "topic" type, durable=false
    }

    public void close() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

    public void start() throws IOException{
        // stub
    }
}