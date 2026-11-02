package com.minijava.ui;

import java.io.IOException;

/**
 * Entry point for the MiniJava-UI backend.
 *
 * <p>Starts the {@link TransformServer}, which exposes {@code POST /api/transform}.
 * The static playground lives in the separate {@code ui} module and is not served
 * by this process. Usage:</p>
 * <pre>
 *   java -jar backend/target/minijava-ui-backend.jar [port]
 *   java -cp backend/target/classes com.minijava.ui.Main [port]
 * </pre>
 *
 * <p>The listen port is the first argument, or the {@code PORT} environment
 * variable, or {@code 8080}. Cross-origin calls from the UI are allowed from
 * {@code CORS_ORIGIN} (default {@code *}).</p>
 */
public final class Main {

    private static final int DEFAULT_PORT = 8080;

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        String corsOrigin = resolveCorsOrigin();

        TransformServer server = new TransformServer(port, corsOrigin);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        System.out.println("MiniJava-UI backend is running.");
        System.out.println("  Catalog: GET  http://localhost:" + port + "/api/catalog");
        System.out.println("  API:     POST http://localhost:" + port + "/api/transform");
        System.out.println("  CORS:   " + corsOrigin);
        System.out.println("Press Ctrl+C to stop.");
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Integer.parseInt(args[0].trim());
        }
        String env = System.getenv("PORT");
        if (env != null && !env.isBlank()) {
            return Integer.parseInt(env.trim());
        }
        return DEFAULT_PORT;
    }

    /** Browser origin allowed to call the API. {@code *} allows any UI host. */
    private static String resolveCorsOrigin() {
        String configured = System.getenv("CORS_ORIGIN");
        if (configured == null || configured.isBlank()) {
            return "*";
        }
        return configured.trim();
    }
}
