package com.oc.ssdlc;


import com.oc.ssdlc.RabbitMQClient;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ClientLauncher {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar smarthome-all-clients-1.0-SNAPSHOT-jar-with-dependencies.jar <client_type> <device_id>");
            System.out.println("Available client_types: thermostat, doorlock");
            System.out.println("Example: java -jar smarthome-all-clients-1.0-SNAPSHOT-jar-with-dependencies.jar thermostat 101");
            System.out.println("Example: java -jar smarthome-all-clients-1.0-SNAPSHOT-jar-with-dependencies.jar doorlock frontdoor");
            return;
        }

        String clientType = args[0].toLowerCase();
        String deviceId = args[1];

        com.oc.ssdlc.RabbitMQClient client = null;

        try {
            switch (clientType) {
                case "thermostat":
                    client = new ThermostatClient(deviceId);
                    break;
                case "doorlock":
                    client = new DoorlockClient(deviceId);
                    break;
                default:
                    System.err.println("Unknown client type: " + clientType);
                    System.out.println("Available client_types: thermostat, doorlock");
                    return;
            }

            final RabbitMQClient finalClient = client; // Für den Shutdown-Hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    finalClient.close();
                    System.out.println("Client " + deviceId + " of type " + clientType + " closed.");
                } catch (IOException | TimeoutException e) {
                    System.err.println("Error closing client " + deviceId + ": " + e.getMessage());
                }
            }));

            client.start(); // Startet die Logik des jeweiligen Clients

        } catch (IOException | TimeoutException e) {
            System.err.println("Failed to start client " + clientType + " " + deviceId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}