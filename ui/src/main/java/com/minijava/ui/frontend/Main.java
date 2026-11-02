package com.minijava.ui.frontend;

import java.io.IOException;

/**
 * Entry point for the MiniJava-UI frontend module.
 *
 * <p>Serves the dynamic playground. Transformation calls go to the separate
 * backend module. Usage:</p>
 * <pre>
 *   java -jar ui/target/minijava-ui-frontend.jar [port]
 * </pre>
 *
 * <p>The listen port is the first argument, or {@code UI_PORT}, or {@code 3000}.</p>
 */
public final class Main {

    private static final int DEFAULT_PORT = 3000;

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        StaticServer server = new StaticServer(port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        System.out.println("MiniJava-UI frontend is running.");
        System.out.println("  Playground: http://localhost:" + port + "/");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Integer.parseInt(args[0].trim());
        }
        String env = System.getenv("UI_PORT");
        if (env != null && !env.isBlank()) {
            return Integer.parseInt(env.trim());
        }
        return DEFAULT_PORT;
    }
}
