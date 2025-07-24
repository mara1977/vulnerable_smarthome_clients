package com.oc.ssdlc;

import com.rabbitmq.client.DeliverCallback;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ThermostatClient extends RabbitMQClient {

    private final String deviceId;
    private double currentTemperature = 20.0;
    private double targetTemperature = 20.0;
    private final Random random = new Random();
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ThermostatClient(String deviceId) throws IOException, TimeoutException {
        super();
        this.deviceId = deviceId;
        System.out.println("Thermostat " + deviceId + " connected to RabbitMQ.");
    }

    public double getCurrentTemperature() {
        return currentTemperature;
    }

    public void start() throws IOException {
        String commandQueueName = channel.queueDeclare().getQueue();
        channel.queueBind(commandQueueName, EXCHANGE_NAME, "thermostat." + deviceId + ".#");

        System.out.println(" [*] Thermostat " + deviceId + " waiting for commands. Current temp: " + currentTemperature);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try {
                JSONObject jsonMessage = new JSONObject(message);

                if (jsonMessage.has("command")) {
                    String command = jsonMessage.getString("command");
                    String value = jsonMessage.getString("value");
                    System.out.println(" [x] Thermostat " + deviceId + " received command: '" + message + "'");

                    if (command.equals("thermostat." + deviceId + ".settemp")) {
                        try {
                            double newTargetTemp = Double.parseDouble(value);
                            this.targetTemperature = newTargetTemp;
                            System.out.println("     -> Thermostat " + deviceId + ": Target temperature set to " + newTargetTemp + "°C.");
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid temperature value: " + value);
                        }
                    } else {
                        System.out.println("     -> Unknown command for Thermostat " + deviceId + ": " + command);
                    }
                } else if (jsonMessage.has("type") && "temperature_update".equals(jsonMessage.getString("type"))) {
                    // Ignore self-published temperature updates to avoid redundant logging
                } else {
                    System.out.println(" [x] Thermostat " + deviceId + " received unhandled message: '" + message + "'");
                }
            } catch (JSONException e) {
                System.err.println("Error parsing JSON for Thermostat " + deviceId + ": " + e.getMessage() + ". Message: " + message);
            } catch (Exception e) {
                System.err.println("An unexpected error occurred for Thermostat " + deviceId + ": " + e.getMessage() + ". Message: " + message);
            }
        };
        channel.basicConsume(commandQueueName, true, deliverCallback, consumerTag -> {});

        scheduler.scheduleAtFixedRate(this::publishTemperature, 0, 3, TimeUnit.SECONDS);
    }

    private void publishTemperature() {
        try {
            if (currentTemperature < targetTemperature) {
                currentTemperature += 0.1 + random.nextDouble() * 0.1;
            } else if (currentTemperature > targetTemperature) {
                currentTemperature -= 0.1 + random.nextDouble() * 0.1;
            }
            currentTemperature += (random.nextDouble() - 0.5) * 0.1;
            currentTemperature = Math.round(currentTemperature * 10.0) / 10.0;

            JSONObject message = new JSONObject();
            message.put("device_id", deviceId);
            message.put("type", "temperature_update");
            message.put("temperature", currentTemperature);
            message.put("target_temperature", targetTemperature);
            message.put("timestamp", System.currentTimeMillis());

            String routingKey = "thermostat." + deviceId + ".temperature";
            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Thermostat " + deviceId + " sent: '" + message.toString() + "'");
        } catch (IOException e) {
            System.err.println("Error publishing temperature for Thermostat " + deviceId + ": " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException, TimeoutException {
        scheduler.shutdownNow();
        super.close();
        System.out.println("Thermostat " + deviceId + " closed.");
    }

    // Removed main method here. It will be launched by ClientLauncher.
}