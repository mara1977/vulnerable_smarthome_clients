package com.oc.ssdlc;


import com.rabbitmq.client.DeliverCallback;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DoorlockClient extends RabbitMQClient {

    private final String deviceId;
    private boolean isLocked = true; // Initial state: locked

    public DoorlockClient(String deviceId) throws IOException, TimeoutException {
        super();
        this.deviceId = deviceId;
        System.out.println("Doorlock " + deviceId + " connected to RabbitMQ.");
    }

    public void start() throws IOException {
        String commandQueueName = channel.queueDeclare().getQueue();
        channel.queueBind(commandQueueName, EXCHANGE_NAME, "doorlock." + deviceId + ".#");

        System.out.println(" [*] Doorlock " + deviceId + " waiting for commands. Current state: " + (isLocked ? "LOCKED" : "UNLOCKED"));

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try {
                JSONObject jsonMessage = new JSONObject(message);

                if (jsonMessage.has("command")) {
                    String command = jsonMessage.getString("command");
                    String value = jsonMessage.getString("value");
                    System.out.println(" [x] Doorlock " + deviceId + " received command: '" + message + "'");

                    if (command.equals("doorlock." + deviceId + ".lock") && "true".equalsIgnoreCase(value)) {
                        setLocked(true);
                        System.out.println("     -> Doorlock " + deviceId + ": LOCKED! 🔒");
                        publishStatus();
                    } else if (command.equals("doorlock." + deviceId + ".unlock") && "true".equalsIgnoreCase(value)) {
                        setLocked(false);
                        System.out.println("     -> Doorlock " + deviceId + ": UNLOCKED! 🔓");
                        publishStatus();
                    } else {
                        System.out.println("     -> Unknown command or value for Doorlock " + deviceId + ": " + command + ":" + value);
                    }
                } else if (jsonMessage.has("type") && "status_update".equals(jsonMessage.getString("type"))) {
                    // Ignore self-published status updates
                } else {
                    System.out.println(" [x] Doorlock " + deviceId + " received unhandled message: '" + message + "'");
                }
            } catch (JSONException e) {
                System.err.println("Error parsing JSON for Doorlock " + deviceId + ": " + e.getMessage() + ". Message: " + message);
            } catch (Exception e) {
                System.err.println("An unexpected error occurred for Doorlock " + deviceId + ": " + e.getMessage() + ". Message: " + message);
            }
        };
        channel.basicConsume(commandQueueName, true, deliverCallback, consumerTag -> {});

        // Publish initial status and then periodically
        publishStatus();
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::publishStatus, 10, 20, TimeUnit.SECONDS);
    }

    private void setLocked(boolean locked) {
        this.isLocked = locked;
    }

    private void publishStatus() {
        try {
            JSONObject message = new JSONObject();
            message.put("device_id", deviceId);
            message.put("type", "status_update");
            message.put("status", isLocked ? "LOCKED" : "UNLOCKED");
            message.put("timestamp", System.currentTimeMillis());

            String routingKey = "doorlock." + deviceId + ".status";
            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Doorlock " + deviceId + " sent: '" + message.toString() + "'");
        } catch (IOException e) {
            System.err.println("Error publishing status for Doorlock " + deviceId + ": " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException, TimeoutException {
        super.close();
        System.out.println("Doorlock " + deviceId + " closed.");
    }

    // Removed main method here. It will be launched by ClientLauncher.
}