package com.minijava.ui.frontend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Serves the playground assets packaged in this module. It does not implement
 * transformations; those stay in the backend module.
 */
public final class StaticServer {

    private static final String CLASSPATH_ROOT = "webapp/";

    private final int port;
    private HttpServer server;

    public StaticServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String relative = "/".equals(rawPath) ? "index.html" : rawPath.substring(1);
        if (!isSafeRelative(relative)) {
            send(exchange, 404, "text/plain; charset=utf-8", "404 Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] bytes = load(relative);
        if (bytes == null) {
            send(exchange, 404, "text/plain; charset=utf-8", "404 Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(exchange, 200, contentType(relative), bytes);
    }

    private static byte[] load(String relative) throws IOException {
        try (InputStream in = StaticServer.class.getClassLoader().getResourceAsStream(CLASSPATH_ROOT + relative)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private static boolean isSafeRelative(String relative) {
        if (relative.isEmpty() || relative.startsWith("/")) {
            return false;
        }
        for (String segment : relative.split("/")) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
