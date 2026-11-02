package com.minijava.ui;

import com.minijava.ui.json.MiniJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP API for the Java UI-transformation engine.
 *
 * <p>Built on the JDK's {@code com.sun.net.httpserver} so the backend runs with
 * no third-party dependencies. The static playground is a separate module and is
 * not served here. Endpoints:</p>
 * <ul>
 *   <li>{@code GET /} &rarr; a short JSON description of this service.</li>
 *   <li>{@code GET /api/catalog} &rarr; transformation names plus the sample
 *       config, so the UI module can build its controls from the API.</li>
 *   <li>{@code POST /api/transform} &rarr; run the transforms and return the
 *       rewritten config plus rendered HTML as JSON.</li>
 * </ul>
 *
 * <p>Responses include CORS headers so a UI deployed on another origin can call
 * the API directly.</p>
 */
public final class TransformServer {

    private final int port;
    private final String corsOrigin;
    private final UiTransformer transformer = new UiTransformer();
    private HttpServer server;

    /**
     * @param port       port to listen on
     * @param corsOrigin value for {@code Access-Control-Allow-Origin}
     */
    public TransformServer(int port, String corsOrigin) {
        this.port = port;
        this.corsOrigin = corsOrigin;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/catalog", this::handleCatalog);
        server.createContext("/api/transform", this::handleTransform);
        server.createContext("/", this::handleRoot);
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---- GET / --------------------------------------------------------------

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path)) {
            sendJson(exchange, 404, "{\"error\":\"not found\"}");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"use GET\"}");
            return;
        }
        sendJson(exchange, 200,
                "{\"service\":\"minijava-ui-backend\",\"catalog\":\"GET /api/catalog\",\"transform\":\"POST /api/transform\"}");
    }

    // ---- GET /api/catalog ----------------------------------------------------

    private void handleCatalog(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"use GET\"}");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transforms", UiTransformer.catalog());
        body.put("sample", UiTransformer.sampleConfig());
        sendJson(exchange, 200, UiTransformer.toJson(body));
    }

    // ---- POST /api/transform -------------------------------------------------

    private void handleTransform(HttpExchange exchange) throws IOException {
        try {
            if (preflight(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"use POST\"}");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> request = MiniJson.parseObject(body);

            Object config = request.get("config");
            if (config == null) {
                sendJson(exchange, 400, "{\"error\":\"missing 'config'\"}");
                return;
            }
            String configJson = UiTransformer.toJson(config);

            List<String> transforms = new ArrayList<>();
            Object rawTransforms = request.get("transforms");
            if (rawTransforms instanceof List) {
                for (Object t : (List<?>) rawTransforms) {
                    if (t != null) {
                        transforms.add(t.toString());
                    }
                }
            }

            UiTransformer.Result result = transformer.transform(configJson, transforms);
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"applied\":[");
            for (int i = 0; i < result.applied.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append('"').append(MiniJson.escape(result.applied.get(i))).append('"');
            }
            json.append("],");
            json.append("\"config\":").append(UiTransformer.toJson(result.config)).append(',');
            json.append("\"html\":\"").append(MiniJson.escape(result.html)).append('"');
            json.append('}');
            sendJson(exchange, 200, json.toString());
        } catch (RuntimeException ex) {
            sendJson(exchange, 400, "{\"error\":\"" + MiniJson.escape(String.valueOf(ex.getMessage())) + "\"}");
        }
    }

    /** Answer a CORS preflight and return true when the request is finished. */
    private boolean preflight(HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            return false;
        }
        addCors(exchange);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return true;
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        addCors(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Utility for tests / callers that just want to drain a stream. */
    static String read(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
