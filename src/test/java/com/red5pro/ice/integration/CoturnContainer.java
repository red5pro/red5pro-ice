/* See LICENSE.md for license information */
package com.red5pro.ice.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a coturn Docker container for integration testing.
 *
 * Coturn is an open-source TURN/STUN server implementation.
 * This class starts a container before tests and stops it after.
 *
 * @author Red5 Pro
 */
public class CoturnContainer {

    private static final Logger logger = LoggerFactory.getLogger(CoturnContainer.class);

    /** Docker image to use for coturn */
    public static final String COTURN_IMAGE = "coturn/coturn:latest";

    /** Container name for easy identification and cleanup */
    public static final String CONTAINER_NAME = "red5pro-ice-test-coturn";

    /** Default STUN/TURN port */
    public static final int STUN_PORT = 3478;

    /** Default TURN TLS port */
    public static final int TURN_TLS_PORT = 5349;

    /** Default realm for TURN authentication */
    public static final String REALM = "red5pro.com";

    /** Default test username */
    public static final String USERNAME = "testuser";

    /** Default test password */
    public static final String PASSWORD = "testpass";

    private String containerId;
    private boolean running;

    /**
     * Checks if Docker is available on the system.
     *
     * @return true if Docker is available and running
     */
    public static boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (Exception e) {
            logger.debug("Docker not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Starts the coturn container.
     *
     * @return true if the container started successfully
     * @throws IOException if there's an error starting the container
     * @throws InterruptedException if the process is interrupted
     */
    public boolean start() throws IOException, InterruptedException {
        if (running) {
            logger.warn("Container already running");
            return true;
        }

        // First, clean up any existing container with the same name
        cleanup();

        logger.info("Starting coturn container...");

        // Build the docker run command
        // coturn configuration via command line arguments
        ProcessBuilder pb = new ProcessBuilder("docker", "run", "-d", "--name", CONTAINER_NAME, "-p", STUN_PORT + ":" + STUN_PORT + "/udp",
                "-p", STUN_PORT + ":" + STUN_PORT + "/tcp", "-p", TURN_TLS_PORT + ":" + TURN_TLS_PORT + "/udp", "-p",
                TURN_TLS_PORT + ":" + TURN_TLS_PORT + "/tcp",
                // Relay ports range
                "-p", "49152-49162:49152-49162/udp", COTURN_IMAGE,
                // coturn arguments
                "-n",  // no configuration file
                "--log-file=stdout", "--min-port=49152", "--max-port=49162", "--realm=" + REALM, "--user=" + USERNAME + ":" + PASSWORD,
                // Allow localhost connections for testing
                "--listening-ip=0.0.0.0", "--relay-ip=0.0.0.0",
                // Verbose logging for debugging
                "--verbose");

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read the container ID
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            containerId = reader.readLine();
        }

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed || process.exitValue() != 0) {
            logger.error("Failed to start coturn container");
            return false;
        }

        if (containerId == null || containerId.isEmpty()) {
            logger.error("No container ID returned");
            return false;
        }

        logger.info("Coturn container started with ID: {}", containerId.substring(0, 12));

        // Wait for the container to be ready
        running = waitForReady(10);
        if (running) {
            logger.info("Coturn container is ready and accepting connections");
        } else {
            logger.error("Coturn container failed to become ready");
            stop();
        }

        return running;
    }

    /**
     * Waits for the coturn server to be ready to accept connections.
     *
     * @param timeoutSeconds maximum time to wait
     * @return true if the server is ready
     */
    private boolean waitForReady(int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            if (isStunResponding()) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Checks if the STUN server is responding to binding requests.
     *
     * @return true if the server responds
     */
    private boolean isStunResponding() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1000);

            // Simple STUN Binding Request
            // 20 bytes: 2 type + 2 length + 4 magic cookie + 12 transaction ID
            byte[] request = new byte[20];
            // Binding Request type: 0x0001
            request[0] = 0x00;
            request[1] = 0x01;
            // Message length: 0 (no attributes)
            request[2] = 0x00;
            request[3] = 0x00;
            // Magic cookie: 0x2112A442
            request[4] = 0x21;
            request[5] = 0x12;
            request[6] = (byte) 0xA4;
            request[7] = 0x42;
            // Transaction ID (random 12 bytes)
            for (int i = 8; i < 20; i++) {
                request[i] = (byte) (Math.random() * 256);
            }

            InetAddress address = InetAddress.getByName("127.0.0.1");
            DatagramPacket packet = new DatagramPacket(request, request.length, address, STUN_PORT);
            socket.send(packet);

            byte[] response = new byte[256];
            DatagramPacket responsePacket = new DatagramPacket(response, response.length);
            socket.receive(responsePacket);

            // Check if it's a Binding Success Response (0x0101)
            return responsePacket.getLength() >= 20 && response[0] == 0x01 && response[1] == 0x01;

        } catch (Exception e) {
            logger.trace("STUN not yet responding: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Stops the coturn container.
     */
    public void stop() {
        if (containerId == null) {
            return;
        }

        logger.info("Stopping coturn container...");

        try {
            Process process = new ProcessBuilder("docker", "stop", containerId).redirectErrorStream(true).start();
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Error stopping container: {}", e.getMessage());
        }

        cleanup();
        running = false;
        containerId = null;
    }

    /**
     * Removes any existing container with our name.
     */
    private void cleanup() {
        try {
            Process process = new ProcessBuilder("docker", "rm", "-f", CONTAINER_NAME).redirectErrorStream(true).start();
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore - container may not exist
        }
    }

    /**
     * Gets the STUN server address.
     *
     * @return the STUN server address as "host:port"
     */
    public String getStunAddress() {
        return "127.0.0.1:" + STUN_PORT;
    }

    /**
     * Gets the TURN server address.
     *
     * @return the TURN server address as "host:port"
     */
    public String getTurnAddress() {
        return "127.0.0.1:" + STUN_PORT;
    }

    /**
     * @return true if the container is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets container logs for debugging.
     *
     * @return the container logs
     */
    public String getLogs() {
        if (containerId == null) {
            return "";
        }

        try {
            Process process = new ProcessBuilder("docker", "logs", containerId).redirectErrorStream(true).start();

            StringBuilder logs = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append(line).append("\n");
                }
            }

            process.waitFor(5, TimeUnit.SECONDS);
            return logs.toString();

        } catch (Exception e) {
            return "Error getting logs: " + e.getMessage();
        }
    }
}
