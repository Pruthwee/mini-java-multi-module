package com.minijava.ui;

import com.minijava.ui.json.MiniJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Core of MiniJava-UI: applies named transformations to a declarative UI config
 * and renders the result to HTML.
 *
 * <p>A UI config is a JSON object of the shape:</p>
 * <pre>
 * {
 *   "title": "Login",
 *   "theme": "light",
 *   "components": [
 *     {"type": "heading", "text": "Sign in"},
 *     {"type": "label",   "text": "Username"},
 *     {"type": "input",   "name": "username", "placeholder": "you@example.com"},
 *     {"type": "button",  "text": "Log In"}
 *   ]
 * }
 * </pre>
 *
 * <p>Each transformation is a small, pure rewrite of that config. The same
 * transformation names and semantics are mirrored in the JavaScript front end
 * ({@code ui/src/main/webapp/js/app.js}) so the playground behaves identically whether it
 * runs client-side or against this Java backend.</p>
 */
public final class UiTransformer {

    /** The transformations this engine understands, in menu order. */
    public static List<String> availableTransforms() {
        List<String> list = new ArrayList<>();
        for (Map<String, String> item : catalog()) {
            list.add(item.get("name"));
        }
        return list;
    }

    /**
     * Name, label, and description for each transformation, in menu order.
     * The UI module renders its checklist from this catalog.
     */
    public static List<Map<String, String>> catalog() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(meta("uppercase-labels", "Uppercase labels & headings",
                "Uppercases the text of label and heading components"));
        list.add(meta("dark-theme", "Switch to dark theme",
                "Sets the config theme to dark"));
        list.add(meta("mark-inputs-required", "Mark inputs required",
                "Adds required to every input"));
        list.add(meta("add-aria-labels", "Add ARIA labels to inputs",
                "Derives an aria-label for each input from its name"));
        return list;
    }

    /** The config the playground loads when the user asks for a sample. */
    public static Map<String, Object> sampleConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", "Login");
        config.put("theme", "light");
        List<Map<String, Object>> components = new ArrayList<>();
        components.add(textComponent("heading", "Sign in to your account"));
        components.add(textComponent("label", "Username"));
        components.add(inputComponent("username", "you@example.com", "text"));
        components.add(textComponent("label", "Password"));
        components.add(inputComponent("password", "", "password"));
        components.add(textComponent("button", "Log In"));
        config.put("components", components);
        return config;
    }

    private static Map<String, String> meta(String name, String label, String description) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("label", label);
        map.put("description", description);
        return map;
    }

    private static Map<String, Object> textComponent(String type, String text) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("type", type);
        component.put("text", text);
        return component;
    }

    private static Map<String, Object> inputComponent(String name, String placeholder, String inputType) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("type", "input");
        component.put("name", name);
        if (placeholder != null && !placeholder.isEmpty()) {
            component.put("placeholder", placeholder);
        }
        if (inputType != null && !"text".equals(inputType)) {
            component.put("inputType", inputType);
        }
        return component;
    }

    /** Result of a transform run: the rewritten config and its rendered HTML. */
    public static final class Result {
        public final Map<String, Object> config;
        public final String html;
        public final List<String> applied;

        Result(Map<String, Object> config, String html, List<String> applied) {
            this.config = config;
            this.html = html;
            this.applied = applied;
        }
    }

    /**
     * Parse {@code configJson}, apply each transform in order, and render HTML.
     *
     * @param configJson the UI config as JSON
     * @param transforms transform names to apply (unknown names are ignored)
     * @return the transformed config plus rendered HTML
     */
    public Result transform(String configJson, List<String> transforms) {
        Map<String, Object> config = MiniJson.parseObject(configJson);
        List<String> applied = new ArrayList<>();
        List<String> requested = transforms == null ? List.of() : transforms;
        for (String name : requested) {
            if (applyTransform(config, name)) {
                applied.add(name);
            }
        }
        return new Result(config, render(config), applied);
    }

    private boolean applyTransform(Map<String, Object> config, String name) {
        switch (name) {
            case "uppercase-labels": uppercaseLabels(config); return true;
            case "dark-theme": config.put("theme", "dark"); return true;
            case "mark-inputs-required": markInputsRequired(config); return true;
            case "add-aria-labels": addAriaLabels(config); return true;
            default: return false;
        }
    }

    private void uppercaseLabels(Map<String, Object> config) {
        for (Map<String, Object> c : components(config)) {
            String type = str(c.get("type"));
            if ("label".equals(type) || "heading".equals(type)) {
                Object text = c.get("text");
                if (text != null) {
                    c.put("text", str(text).toUpperCase(Locale.ROOT));
                }
            }
        }
    }

    private void markInputsRequired(Map<String, Object> config) {
        for (Map<String, Object> c : components(config)) {
            if ("input".equals(str(c.get("type")))) {
                c.put("required", Boolean.TRUE);
            }
        }
    }

    private void addAriaLabels(Map<String, Object> config) {
        for (Map<String, Object> c : components(config)) {
            if ("input".equals(str(c.get("type"))) && c.get("ariaLabel") == null) {
                String name = str(c.getOrDefault("name", "field"));
                c.put("ariaLabel", humanize(name));
            }
        }
    }

    /** Render a UI config to an HTML fragment. */
    public String render(Map<String, Object> config) {
        String theme = str(config.getOrDefault("theme", "light"));
        String title = str(config.getOrDefault("title", "Untitled UI"));
        StringBuilder sb = new StringBuilder();
        sb.append("<section class=\"ui-card theme-").append(esc(theme)).append("\">\n");
        sb.append("  <div class=\"ui-card__title\">").append(esc(title)).append("</div>\n");
        for (Map<String, Object> c : components(config)) {
            sb.append("  ").append(renderComponent(c)).append("\n");
        }
        sb.append("</section>");
        return sb.toString();
    }

    private String renderComponent(Map<String, Object> c) {
        String type = str(c.getOrDefault("type", "text"));
        String text = str(c.getOrDefault("text", ""));
        switch (type) {
            case "heading":
                return "<h2 class=\"ui-heading\">" + esc(text) + "</h2>";
            case "label":
                return "<label class=\"ui-label\">" + esc(text) + "</label>";
            case "input": {
                String name = str(c.getOrDefault("name", "field"));
                String inputType = str(c.getOrDefault("inputType", "text"));
                String placeholder = str(c.getOrDefault("placeholder", ""));
                boolean required = truthy(c.get("required"));
                StringBuilder in = new StringBuilder();
                in.append("<input class=\"ui-input\" type=\"").append(esc(inputType)).append("\"");
                in.append(" name=\"").append(esc(name)).append("\"");
                if (!placeholder.isEmpty()) {
                    in.append(" placeholder=\"").append(esc(placeholder)).append("\"");
                }
                if (c.get("ariaLabel") != null) {
                    in.append(" aria-label=\"").append(esc(str(c.get("ariaLabel")))).append("\"");
                }
                if (required) {
                    in.append(" required");
                }
                in.append(" />");
                return in.toString();
            }
            case "button":
                return "<button class=\"ui-button\">" + esc(text) + "</button>";
            case "text":
            default:
                return "<p class=\"ui-text\">" + esc(text) + "</p>";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> components(Map<String, Object> config) {
        Object raw = config.get("components");
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                if (item instanceof Map) {
                    out.add((Map<String, Object>) item);
                }
            }
        }
        return out;
    }

    /** Serialize a config back to compact JSON (used for the "after" config view). */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(MiniJson.escape(e.getKey())).append("\":");
                writeJson(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<Object>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeJson(item, sb);
            }
            sb.append(']');
        } else if (value instanceof String) {
            sb.append('"').append(MiniJson.escape((String) value)).append('"');
        } else if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append(Long.toString((long) d));
            } else {
                sb.append(d);
            }
        } else {
            sb.append(value.toString());
        }
    }

    private static String humanize(String name) {
        String cleaned = name.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) {
            return "Field";
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        return o != null && "true".equalsIgnoreCase(o.toString());
    }

    /** Minimal HTML text/attribute escaping. */
    static String esc(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }
}
