/*
 * Dynamic playground for the ui module.
 *
 * The page builds its editor from a component model and loads the
 * transformation checklist from the backend module (GET /api/catalog).
 * The same transforms are implemented here so the browser engine still runs
 * when the backend is down. Choosing the Java engine calls POST /api/transform.
 */
(function () {
  "use strict";

  var COMPONENT_TYPES = ["heading", "label", "input", "button", "text"];

  var FALLBACK_SAMPLE = {
    title: "Login",
    theme: "light",
    components: [
      { type: "heading", text: "Sign in to your account" },
      { type: "label", text: "Username" },
      { type: "input", name: "username", placeholder: "you@example.com" },
      { type: "label", text: "Password" },
      { type: "input", name: "password", inputType: "password" },
      { type: "button", text: "Log In" }
    ]
  };

  var FALLBACK_CATALOG = [
    { name: "uppercase-labels", label: "Uppercase labels & headings", description: "Uppercases the text of label and heading components" },
    { name: "dark-theme", label: "Switch to dark theme", description: "Sets the config theme to dark" },
    { name: "mark-inputs-required", label: "Mark inputs required", description: "Adds required to every input" },
    { name: "add-aria-labels", label: "Add ARIA labels to inputs", description: "Derives an aria-label for each input from its name" }
  ];

  var TRANSFORMS = {
    "uppercase-labels": function (config) {
      components(config).forEach(function (c) {
        if ((c.type === "label" || c.type === "heading") && c.text != null) {
          c.text = String(c.text).toUpperCase();
        }
      });
    },
    "dark-theme": function (config) {
      config.theme = "dark";
    },
    "mark-inputs-required": function (config) {
      components(config).forEach(function (c) {
        if (c.type === "input") { c.required = true; }
      });
    },
    "add-aria-labels": function (config) {
      components(config).forEach(function (c) {
        if (c.type === "input" && c.ariaLabel == null) {
          c.ariaLabel = humanize(c.name || "field");
        }
      });
    }
  };

  var state = {
    config: deepClone(FALLBACK_SAMPLE),
    sample: deepClone(FALLBACK_SAMPLE),
    catalog: FALLBACK_CATALOG,
    selected: {},
    engine: "js",
    backend: "checking"
  };

  var runToken = 0;
  var timer = null;

  function deepClone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function components(config) {
    return Array.isArray(config.components) ? config.components : [];
  }

  function apiBase() {
    var configured = window.MINIJAVA_API_BASE;
    if (configured == null) { return ""; }
    return String(configured).replace(/\/$/, "");
  }

  function humanize(name) {
    var cleaned = String(name).replace(/[_-]+/g, " ").trim();
    if (!cleaned) { return "Field"; }
    return cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
  }

  function esc(raw) {
    return String(raw)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function renderComponent(c) {
    var type = c.type || "text";
    var text = c.text != null ? c.text : "";
    if (type === "heading") {
      return '<h2 class="ui-heading">' + esc(text) + "</h2>";
    }
    if (type === "label") {
      return '<label class="ui-label">' + esc(text) + "</label>";
    }
    if (type === "input") {
      var out = '<input class="ui-input" type="' + esc(c.inputType || "text") + '"';
      out += ' name="' + esc(c.name || "field") + '"';
      if (c.placeholder) { out += ' placeholder="' + esc(c.placeholder) + '"'; }
      if (c.ariaLabel != null) { out += ' aria-label="' + esc(c.ariaLabel) + '"'; }
      if (c.required) { out += " required"; }
      return out + " />";
    }
    if (type === "button") {
      return '<button class="ui-button">' + esc(text) + "</button>";
    }
    return '<p class="ui-text">' + esc(text) + "</p>";
  }

  function render(config) {
    var theme = config.theme || "light";
    var title = config.title || "Untitled UI";
    var parts = ['<section class="ui-card theme-' + esc(theme) + '">'];
    parts.push('  <div class="ui-card__title">' + esc(title) + "</div>");
    components(config).forEach(function (c) {
      parts.push("  " + renderComponent(c));
    });
    parts.push("</section>");
    return parts.join("\n");
  }

  function transformInBrowser(config, names) {
    var working = deepClone(config);
    var applied = [];
    names.forEach(function (name) {
      if (TRANSFORMS[name]) {
        TRANSFORMS[name](working);
        applied.push(name);
      }
    });
    return { config: working, html: render(working), applied: applied };
  }

  function transformOnServer(config, names) {
    return fetch(apiBase() + "/api/transform", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ config: config, transforms: names })
    }).then(function (res) {
      if (!res.ok) { throw new Error("server responded " + res.status); }
      return res.json();
    });
  }

  function blank(type) {
    if (type === "input") {
      return { type: "input", name: "field", placeholder: "", inputType: "text" };
    }
    if (type === "button") { return { type: "button", text: "Continue" }; }
    if (type === "heading") { return { type: "heading", text: "Heading" }; }
    if (type === "label") { return { type: "label", text: "Label" }; }
    return { type: "text", text: "Some text" };
  }

  function selectedNames() {
    return state.catalog.map(function (item) { return item.name; }).filter(function (name) {
      return state.selected[name];
    });
  }

  function setBackend(status) {
    state.backend = status;
    var chip = document.getElementById("backendChip");
    chip.className = "chip" + (status === "connected" ? " ok" : status === "offline" ? " off" : "");
    if (status === "connected") {
      chip.textContent = "backend module · connected";
    } else if (status === "offline") {
      chip.textContent = "backend module · offline";
    } else {
      chip.textContent = "backend module";
    }
  }

  function mount() {
    document.getElementById("app").innerHTML = [
      '<section class="panel controls">',
      '  <h2>Screen</h2>',
      '  <div class="meta-grid">',
      '    <label class="field">Title <input id="screenTitle" type="text" /></label>',
      '    <label class="field">Theme',
      '      <select id="screenTheme"><option value="light">light</option><option value="dark">dark</option></select>',
      '    </label>',
      '  </div>',
      '  <h2>Components</h2>',
      '  <p class="hint">Add and edit components. The preview updates as you change them.</p>',
      '  <div id="components" class="component-list"></div>',
      '  <div class="add-row" id="addRow"></div>',
      '  <div class="row"><button id="loadSample" type="button" class="ghost">Load sample</button></div>',
      '  <details class="advanced">',
      '    <summary>Edit as JSON</summary>',
      '    <textarea id="config" spellcheck="false" aria-label="UI config JSON"></textarea>',
      '    <p id="parseStatus" class="status" role="status"></p>',
      '  </details>',
      '  <h2>Transformations</h2>',
      '  <fieldset id="transforms" class="transforms"></fieldset>',
      '  <h2>Engine</h2>',
      '  <fieldset class="engine">',
      '    <label><input type="radio" name="engine" value="js" checked /> Browser engine in the ui module</label>',
      '    <label><input type="radio" name="engine" value="java" /> Java engine in the backend module</label>',
      '  </fieldset>',
      '  <p id="applied" class="status" role="status"></p>',
      '</section>',
      '<section class="panel results">',
      '  <div class="result-block"><h2>Before</h2><div id="beforePreview" class="preview"></div></div>',
      '  <div class="result-block">',
      '    <h2>After</h2>',
      '    <div id="afterPreview" class="preview"></div>',
      '    <h3>Transformed HTML</h3>',
      '    <pre id="afterHtml" class="code"></pre>',
      '    <h3>Rewritten config</h3>',
      '    <pre id="afterConfig" class="code"></pre>',
      '  </div>',
      '</section>'
    ].join("");

    COMPONENT_TYPES.forEach(function (type) {
      var button = document.createElement("button");
      button.type = "button";
      button.className = "tiny";
      button.textContent = "Add " + type;
      button.addEventListener("click", function () { addComponent(type); });
      document.getElementById("addRow").appendChild(button);
    });

    document.getElementById("screenTitle").addEventListener("input", function (event) {
      state.config.title = event.target.value;
      syncJson();
      schedule();
    });
    document.getElementById("screenTheme").addEventListener("change", function (event) {
      state.config.theme = event.target.value;
      syncJson();
      schedule();
    });
    document.getElementById("loadSample").addEventListener("click", loadSample);
    document.getElementById("config").addEventListener("input", onJsonInput);
    document.getElementById("transforms").addEventListener("change", onTransformChange);
    document.querySelector(".engine").addEventListener("change", function (event) {
      if (event.target.name === "engine") {
        state.engine = event.target.value;
        schedule();
      }
    });

    renderTransforms();
    renderComponents();
    syncMeta();
    syncJson();
    schedule();
    loadCatalog();
  }

  function renderTransforms() {
    var box = document.getElementById("transforms");
    box.innerHTML = "";
    state.catalog.forEach(function (item) {
      var label = document.createElement("label");
      var input = document.createElement("input");
      input.type = "checkbox";
      input.value = item.name;
      input.checked = !!state.selected[item.name];
      var copy = document.createElement("span");
      copy.className = "transform-copy";
      copy.innerHTML = esc(item.label) + "<small>" + esc(item.description || "") + "</small>";
      label.appendChild(input);
      label.appendChild(copy);
      box.appendChild(label);
    });
  }

  function renderComponents() {
    var list = document.getElementById("components");
    var items = components(state.config);
    list.innerHTML = "";
    if (!items.length) {
      list.innerHTML = '<p class="empty-preview">No components yet. Add one below.</p>';
      return;
    }
    items.forEach(function (component, index) {
      list.appendChild(componentCard(component, index, items.length));
    });
  }

  function componentCard(component, index, count) {
    var card = document.createElement("article");
    card.className = "component-card";

    var bar = document.createElement("div");
    bar.className = "component-card__bar";
    var select = document.createElement("select");
    select.setAttribute("aria-label", "Component type");
    COMPONENT_TYPES.forEach(function (type) {
      var option = document.createElement("option");
      option.value = type;
      option.textContent = type;
      if (type === (component.type || "text")) { option.selected = true; }
      select.appendChild(option);
    });
    select.addEventListener("change", function () {
      state.config.components[index] = blank(select.value);
      renderComponents();
      syncJson();
      schedule();
    });
    bar.appendChild(select);
    bar.appendChild(moveButton("Up", index > 0, function () { move(index, -1); }));
    bar.appendChild(moveButton("Down", index < count - 1, function () { move(index, 1); }));
    bar.appendChild(moveButton("Remove", true, function () { removeAt(index); }));
    card.appendChild(bar);

    var fields = document.createElement("div");
    fields.className = "fields";
    fieldDefs(component).forEach(function (def) {
      fields.appendChild(fieldInput(def, component, index));
    });
    card.appendChild(fields);
    return card;
  }

  function moveButton(text, enabled, onClick) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "tiny";
    button.textContent = text;
    button.disabled = !enabled;
    button.addEventListener("click", onClick);
    return button;
  }

  function fieldDefs(component) {
    if (component.type === "input") {
      return [
        { key: "name", label: "Name" },
        { key: "placeholder", label: "Placeholder" },
        { key: "inputType", label: "Input type", options: ["text", "password", "email"] }
      ];
    }
    return [{ key: "text", label: "Text" }];
  }

  function fieldInput(def, component, index) {
    var label = document.createElement("label");
    label.className = "field";
    label.appendChild(document.createTextNode(def.label));
    var input;
    if (def.options) {
      input = document.createElement("select");
      def.options.forEach(function (optionValue) {
        var option = document.createElement("option");
        option.value = optionValue;
        option.textContent = optionValue;
        if ((component[def.key] || "text") === optionValue) { option.selected = true; }
        input.appendChild(option);
      });
      input.addEventListener("change", function () {
        state.config.components[index][def.key] = input.value;
        syncJson();
        schedule();
      });
    } else {
      input = document.createElement("input");
      input.type = "text";
      input.value = component[def.key] != null ? component[def.key] : "";
      input.addEventListener("input", function () {
        state.config.components[index][def.key] = input.value;
        syncJson();
        schedule();
      });
    }
    label.appendChild(input);
    return label;
  }

  function addComponent(type) {
    if (!Array.isArray(state.config.components)) { state.config.components = []; }
    state.config.components.push(blank(type));
    renderComponents();
    syncJson();
    schedule();
  }

  function removeAt(index) {
    state.config.components.splice(index, 1);
    renderComponents();
    syncJson();
    schedule();
  }

  function move(index, delta) {
    var items = state.config.components;
    var next = index + delta;
    var current = items[index];
    items[index] = items[next];
    items[next] = current;
    renderComponents();
    syncJson();
    schedule();
  }

  function syncMeta() {
    var title = document.getElementById("screenTitle");
    var theme = document.getElementById("screenTheme");
    if (document.activeElement !== title) { title.value = state.config.title || ""; }
    var themeValue = state.config.theme === "dark" ? "dark" : "light";
    if (document.activeElement !== theme) { theme.value = themeValue; }
  }

  function syncJson() {
    var box = document.getElementById("config");
    if (document.activeElement === box) { return; }
    box.value = JSON.stringify(state.config, null, 2);
    document.getElementById("parseStatus").textContent = "valid JSON";
    document.getElementById("parseStatus").className = "status ok";
  }

  function onJsonInput() {
    var box = document.getElementById("config");
    var status = document.getElementById("parseStatus");
    try {
      var parsed = JSON.parse(box.value);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("config must be a JSON object");
      }
      state.config = parsed;
      if (!Array.isArray(state.config.components)) { state.config.components = []; }
      status.textContent = "valid JSON";
      status.className = "status ok";
      syncMeta();
      renderComponents();
      schedule();
    } catch (error) {
      status.textContent = "invalid JSON: " + error.message;
      status.className = "status error";
    }
  }

  function onTransformChange(event) {
    if (event.target.type !== "checkbox") { return; }
    state.selected[event.target.value] = event.target.checked;
    schedule();
  }

  function loadSample() {
    state.config = deepClone(state.sample);
    syncMeta();
    renderComponents();
    syncJson();
    schedule();
  }

  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(refresh, 180);
  }

  function refresh() {
    var token = ++runToken;
    var config = state.config;
    document.getElementById("beforePreview").innerHTML = render(config);
    var names = selectedNames();
    if (state.engine === "java") {
      setStatus("Asking the backend module…", "");
      transformOnServer(config, names).then(function (result) {
        if (token !== runToken) { return; }
        setBackend("connected");
        showResult(result);
      }).catch(function (error) {
        if (token !== runToken) { return; }
        setBackend("offline");
        setStatus("backend error: " + error.message, "error");
      });
      return;
    }
    showResult(transformInBrowser(config, names));
  }

  function showResult(result) {
    document.getElementById("afterPreview").innerHTML = result.html || "";
    document.getElementById("afterHtml").textContent = result.html || "";
    document.getElementById("afterConfig").textContent = JSON.stringify(result.config, null, 2);
    var applied = result.applied || [];
    setStatus(applied.length ? "applied: " + applied.join(", ") : "no transformations selected", "ok");
  }

  function setStatus(text, kind) {
    var el = document.getElementById("applied");
    el.textContent = text;
    el.className = "status" + (kind ? " " + kind : "");
  }

  function loadCatalog() {
    fetch(apiBase() + "/api/catalog").then(function (res) {
      if (!res.ok) { throw new Error("catalog " + res.status); }
      return res.json();
    }).then(function (body) {
      if (Array.isArray(body.transforms) && body.transforms.length) {
        state.catalog = body.transforms;
        renderTransforms();
      }
      if (body.sample && typeof body.sample === "object") {
        state.sample = body.sample;
      }
      setBackend("connected");
    }).catch(function () {
      setBackend("offline");
    });
  }

  mount();
})();
