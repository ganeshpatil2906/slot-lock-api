package com.gp.slotsync.e2e.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Manages an embedded HTTP server serving static frontend files and mock API responses
 * if the full backend is not currently running on port 8088 during test execution.
 */
public class EmbeddedServerManager {

    private static HttpServer server;
    private static boolean startedBySuite = false;

    public static synchronized void startIfNotRunning(int port) {
        if (isPortInUse(port)) {
            System.out.println("SlotSync server is already running on port " + port + ". Using existing server.");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            File staticDir = new File("src/main/resources/static");

            // Serve static files and fallback mock endpoints
            server.createContext("/", new StaticFileAndApiHandler(staticDir));
            server.setExecutor(null); // default executor
            server.start();
            startedBySuite = true;
            System.out.println("Started embedded mock/static server on port " + port + " for E2E tests.");
        } catch (IOException e) {
            System.err.println("Could not start embedded test server: " + e.getMessage());
        }
    }

    public static synchronized void stopIfStarted() {
        if (startedBySuite && server != null) {
            server.stop(0);
            startedBySuite = false;
            System.out.println("Stopped embedded test server.");
        }
    }

    private static boolean isPortInUse(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static class StaticFileAndApiHandler implements HttpHandler {
        private final File baseDir;

        public StaticFileAndApiHandler(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            // Handle API mocks when backend is not running
            if (path.startsWith("/api/v1/")) {
                handleApiMock(exchange, path, method);
                return;
            }

            // Default to index.html for root
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File targetFile = new File(baseDir, path.substring(1));
            if (!targetFile.exists() || targetFile.isDirectory()) {
                targetFile = new File(baseDir, "index.html");
            }

            if (!targetFile.exists()) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String contentType = "text/plain";
            String lower = targetFile.getName().toLowerCase();
            if (lower.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (lower.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (lower.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (lower.endsWith(".json")) contentType = "application/json";
            else if (lower.endsWith(".png")) contentType = "image/png";
            else if (lower.endsWith(".svg")) contentType = "image/svg+xml";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, targetFile.length());

            try (FileInputStream fis = new FileInputStream(targetFile);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = fis.read(buffer)) > 0) {
                    os.write(buffer, 0, count);
                }
            }
        }

        private void handleApiMock(HttpExchange exchange, String path, String method) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String response;
            int statusCode = 200;

            if (path.endsWith("/resources")) {
                response = "[{\"id\":1,\"name\":\"Conference Room A\",\"type\":\"MEETING_ROOM\"},{\"id\":2,\"name\":\"Projector HD\",\"type\":\"EQUIPMENT\"}]";
            } else if (path.endsWith("/slots")) {
                response = "[{\"id\":101,\"resourceId\":1,\"resourceName\":\"Conference Room A\",\"startTime\":\"2026-10-15T09:00:00\",\"endTime\":\"2026-10-15T10:00:00\",\"status\":\"AVAILABLE\"}]";
            } else if (path.endsWith("/auth/login") || path.endsWith("/auth/register")) {
                response = "{\"token\":\"mock-jwt-token-for-e2e\",\"tokenType\":\"Bearer\",\"userId\":1,\"name\":\"E2E Test User\",\"email\":\"user@example.com\",\"role\":\"USER\"}";
            } else if (path.endsWith("/bookings/my")) {
                response = "[]";
            } else {
                response = "{\"status\":\"ok\"}";
            }

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
