# MiniJava-UI

A small **UI-transformation testing application**: an HTML/CSS/JS playground
paired with a Java backend that applies the same transformations. It exists to test and
demonstrate UI-transformation flows end-to-end &mdash; you describe a UI as a JSON config,
apply a set of transformations, and inspect the *before* and *after* rendered output, run
either in the browser or by the Java engine.

`mvn package` builds **two modules**:

| Module | Artifact | What it is |
| --- | --- | --- |
| `backend` | `minijava-ui-backend.jar` | Java API. `GET /api/catalog` and `POST /api/transform`. |
| `ui` | `minijava-ui-frontend.jar` | Dynamic playground. It builds the editor in the browser and calls the API. |

It is a companion to
[`mini-java-app`](https://github.com/Pruthwee/mini-java-app) and follows the same spirit:
a compact, self-contained Java demo (Apache-2.0 licensed) used to exercise an agent-driven
flow &mdash; here, UI transformation rather than code upgrade / containerization.

## What it does

A **UI config** is a JSON document describing a UI as a list of components:

```json
{
  "title": "Login",
  "theme": "light",
  "components": [
    { "type": "heading", "text": "Sign in" },
    { "type": "label",   "text": "Username" },
    { "type": "input",   "name": "username", "placeholder": "you@example.com" },
    { "type": "button",  "text": "Log In" }
  ]
}
```

The **transformation engine** rewrites that config and renders it to HTML. Four
transformations ship in the box:

| Transformation | Effect |
| --- | --- |
| `uppercase-labels` | Uppercases the text of `label` and `heading` components |
| `dark-theme` | Switches the config's theme to `dark` |
| `mark-inputs-required` | Adds `required` to every `input` |
| `add-aria-labels` | Derives a human-readable `aria-label` for each `input` from its `name` |

The engine is implemented **twice, in lock-step**: once in Java
(`backend` `UiTransformer`) and once in JavaScript (`ui` `app.js`). The playground builds
its component editor and transformation checklist in the browser. The checklist is loaded
from `GET /api/catalog` when the backend is up, and the preview updates as you edit.
Choose the browser engine or the Java engine (`POST /api/transform`) to confirm a
transformation behaves the same on both sides.

## Project structure

```
MiniJava-UI/
├── pom.xml                          # Maven parent: modules backend and ui
├── LICENSE
├── backend/                         # module 1 — Java API
│   ├── pom.xml
│   └── src/main/java/com/minijava/ui/
│       ├── Main.java
│       ├── TransformServer.java     # GET /api/catalog, POST /api/transform
│       ├── UiTransformer.java
│       └── json/MiniJson.java
└── ui/                              # module 2 — dynamic playground
    ├── pom.xml
    └── src/main/
        ├── java/com/minijava/ui/frontend/
        │   ├── Main.java            # serves the playground
        │   └── StaticServer.java
        └── webapp/
            ├── index.html
            ├── css/styles.css
            └── js/
                ├── config.js        # API origin (MINIJAVA_API_BASE)
                └── app.js           # builds the editor and mirrors the engine
```

## Requirements

- **JDK 17+** (both modules use only the standard library).
- **Maven** builds both modules from the repository root.

## Run both modules

```sh
mvn package
java -jar backend/target/minijava-ui-backend.jar
java -jar ui/target/minijava-ui-frontend.jar
```

The API listens on **http://localhost:8080/**. The playground listens on **http://localhost:3000/**.

`ui/src/main/webapp/js/config.js` sets `MINIJAVA_API_BASE` (default `http://localhost:8080`). Change it when the API is deployed somewhere else. The UI module's port is the first argument, or `UI_PORT`, or `3000`.

### Without Maven (JDK only)

```sh
mkdir -p backend/target/classes
javac -d backend/target/classes $(find backend/src/main/java -name '*.java')
java -cp backend/target/classes com.minijava.ui.Main
```

### Port and CORS

| Setting | Default | Meaning |
| --- | --- | --- |
| first argument, or `PORT` | `8080` | Listen port. Argument wins over `PORT`. |
| `CORS_ORIGIN` | `*` | `Access-Control-Allow-Origin`. Set this to the UI's origin when you want to restrict callers. |

```sh
java -jar backend/target/minijava-ui-backend.jar 9000
CORS_ORIGIN=https://ui.example.com java -jar backend/target/minijava-ui-backend.jar
```

`GET /` returns a short JSON description of the service. The transform API is unchanged:

```sh
curl -X POST http://localhost:8080/api/transform \
  -H 'Content-Type: application/json' \
  -d '{
    "config": {
      "title": "Login",
      "components": [
        { "type": "label", "text": "Username" },
        { "type": "input", "name": "user_name" }
      ]
    },
    "transforms": ["uppercase-labels", "add-aria-labels"]
  }'
```

Response:

```json
{
  "applied": ["uppercase-labels", "add-aria-labels"],
  "config": { "...": "the rewritten UI config" },
  "html": "<section class=\"ui-card theme-light\"> ... </section>"
}
```

## UI module

Open **http://localhost:3000/** after starting the frontend jar.

1. Edit the screen title, theme, and components. The before/after preview updates as you type.
2. Tick transformations. The list is loaded from `GET /api/catalog` when the backend module is running.
3. Choose the browser engine (this module) or the Java engine (the backend module).

The browser engine still runs if the backend is offline. The header shows which modules are up.

## Tests

`UiTransformerTest` covers rendering, each transformation, composition order, unknown-name
handling, and HTML escaping:

```sh
mvn test
```

## License

Apache License 2.0 &mdash; see [`LICENSE`](LICENSE).
