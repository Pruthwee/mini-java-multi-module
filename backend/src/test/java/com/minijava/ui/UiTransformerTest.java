package com.minijava.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the UI-transformation engine. */
class UiTransformerTest {

    private static final String SAMPLE = "{"
            + "\"title\":\"Login\",\"theme\":\"light\","
            + "\"components\":["
            + "{\"type\":\"heading\",\"text\":\"Sign in\"},"
            + "{\"type\":\"label\",\"text\":\"Username\"},"
            + "{\"type\":\"input\",\"name\":\"user_name\",\"placeholder\":\"you@example.com\"},"
            + "{\"type\":\"button\",\"text\":\"Log In\"}"
            + "]}";

    private final UiTransformer transformer = new UiTransformer();

    @Test
    void rendersBaseConfigToHtml() {
        UiTransformer.Result result = transformer.transform(SAMPLE, List.of());
        assertTrue(result.html.contains("<h2 class=\"ui-heading\">Sign in</h2>"));
        assertTrue(result.html.contains("<label class=\"ui-label\">Username</label>"));
        assertTrue(result.html.contains("name=\"user_name\""));
        assertTrue(result.html.contains("<button class=\"ui-button\">Log In</button>"));
        assertTrue(result.html.contains("theme-light"));
    }

    @Test
    void uppercaseLabelsTransformsHeadingsAndLabels() {
        UiTransformer.Result result = transformer.transform(SAMPLE, List.of("uppercase-labels"));
        assertTrue(result.html.contains(">SIGN IN</h2>"));
        assertTrue(result.html.contains(">USERNAME</label>"));
        // Button text is not a label and must remain unchanged.
        assertTrue(result.html.contains(">Log In</button>"));
        assertEquals(List.of("uppercase-labels"), result.applied);
    }

    @Test
    void darkThemeSwitchesTheme() {
        UiTransformer.Result result = transformer.transform(SAMPLE, List.of("dark-theme"));
        assertTrue(result.html.contains("theme-dark"));
        assertFalse(result.html.contains("theme-light"));
    }

    @Test
    void markInputsRequiredAddsRequiredAttribute() {
        UiTransformer.Result result = transformer.transform(SAMPLE, List.of("mark-inputs-required"));
        assertTrue(result.html.contains("required"));
        assertEquals(Boolean.TRUE, inputComponent(result).get("required"));
    }

    @Test
    void addAriaLabelsDerivesHumanReadableLabel() {
        UiTransformer.Result result = transformer.transform(SAMPLE, List.of("add-aria-labels"));
        assertEquals("User name", inputComponent(result).get("ariaLabel"));
        assertTrue(result.html.contains("aria-label=\"User name\""));
    }

    @Test
    void transformsComposeInOrder() {
        UiTransformer.Result result = transformer.transform(
                SAMPLE, List.of("uppercase-labels", "dark-theme", "mark-inputs-required"));
        assertTrue(result.html.contains(">USERNAME</label>"));
        assertTrue(result.html.contains("theme-dark"));
        assertTrue(result.html.contains("required"));
        assertEquals(3, result.applied.size());
    }

    @Test
    void unknownTransformIsIgnored() {
        UiTransformer.Result result = transformer.transform(SAMPLE, List.of("no-such-transform"));
        assertTrue(result.applied.isEmpty());
    }

    @Test
    void escapesHtmlSpecialCharacters() {
        String cfg = "{\"components\":[{\"type\":\"label\",\"text\":\"<b>&hi</b>\"}]}";
        UiTransformer.Result result = transformer.transform(cfg, List.of());
        assertTrue(result.html.contains("&lt;b&gt;&amp;hi&lt;/b&gt;"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> inputComponent(UiTransformer.Result result) {
        List<Object> components = (List<Object>) result.config.get("components");
        for (Object c : components) {
            Map<String, Object> comp = (Map<String, Object>) c;
            if ("input".equals(comp.get("type"))) {
                return comp;
            }
        }
        throw new AssertionError("no input component found");
    }
}
