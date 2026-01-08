---
mode: 'agent'
description: 'Generate project documentation in the docs folder'
---

## Task

You are an assistant that generates production-ready documentation for this Scala project.

The codebase is primarily:
- Scala (Pekko Typed / Pekko HTTP, Circe, Workflows4s)
- Typelevel ecosystem (cats, cats-effect, http4s, fs2, log4cats, weaver-cats)
- LangChain4j (agents and agentic AI, AI services, Ollama)
- SQL (Flyway migrations)
- sbt build

The project implements a trip search agentic workflow using:
- Pekko HTTP for the API layer
- Pekko Typed / Cluster Sharding entities for workflows
- Workflows4s for workflow orchestration
- LangChain4j to build agentic AI applications
- cats-effect to manage side effects and concurrency
- Circe for JSON codecs
- Flyway for database migrations

Your job is to:
1. Inspect the project structure, source code, and configuration.
2. Generate a complete set of documentation files in the `docs` folder.
3. Make the documentation sufficient for a new developer to:
   - Understand the architecture and main components.
   - Set up the development environment.
   - Run the application locally.
   - Run tests.
   - Apply and troubleshoot Flyway migrations.
   - Work with Pekko-based workflows and HTTP routes.
   - Understand JSON codecs based on `io.circe.Codec.AsObject`.
   - Understand how logging is done with log4cats and SLF4J.

## Documentation Guidelines

When generating documentation:

1. **Audience and tone**
   - Target backend Scala developers familiar with sbt and HTTP APIs.
   - Use concise, technical language.
   - Prefer examples and concrete commands over vague descriptions.

2. **Structure**

   At minimum, create the following files in the `docs` folder:

   - `docs/README.md`  
     - High-level project overview and goals.  
     - Key technologies (Scala, sbt, Pekko, Flyway, Circe, Workflows4s, LangChain4j, cats-effect).  
     - High-level architecture diagram or description (API layer, workflow layer, persistence).

   - `docs/getting-started.md`  
     - Prerequisites (JDK version, sbt version, database, Docker).  
     - How to clone the repo and import into IntelliJ IDEA.  
     - How to configure environment variables / config files.  
     - Commands to compile, run, and test the project:
       - `sbt compile`
       - `sbt test`
       - `sbt run` or main class instructions.  
     - How to start supporting services (database, etc.).

   - `docs/architecture.md`  
     - Overview of layers/modules (API, domain, infrastructure, workflow runtime).  
     - How Pekko Typed, Cluster Sharding, and Workflows4s are used.  
     - Description of the workflow entity (e.g. `WorkflowBehavior`) and its commands (including `DeliverSignal`).  
     - Explanation of how ask/response patterns work and common failure modes (e.g. `AskTimeoutException`).  
     - How SLF4J logging is typically wired and used.

   - `docs/api.md`  
     - Document the HTTP endpoints (e.g. trip search routes).  
     - For each endpoint:
       - HTTP method and path.
       - Request/response JSON schema or examples (using Circe codecs).  
       - Expected status codes and error responses.  
       - Note timeouts and interaction with long-running workflows.

   - `docs/workflows.md`  
     - Describe the main workflows (e.g. trip search).  
     - Signals, events, and states used by Workflows4s.  
     - How signals are delivered via Pekko and how replies are handled.  
     - How to debug workflow timeouts and missing replies.  
     - How context/logging is used inside workflow steps.

   - `docs/persistence-and-migrations.md`  
     - Overview of database usage (which DB, schema structure at a high level).  
     - How Flyway is configured in this project (config files, sbt tasks, or startup integration).  
     - How to run migrations locally and in CI/CD.  
     - How to troubleshoot common errors, e.g.  
       - `Detected both transactional and non-transactional statements within the same migration (even though mixed is false)`.  
     - How to split migrations or configure transactional behavior correctly.

   - `docs/development-guide.md`  
     - Coding conventions specific to this project (if derivable from code).  
     - How to add a new HTTP endpoint.  
     - How to add a new workflow or extend an existing one.  
     - How to add new Circe codecs (with `Codec.AsObject`) and schema definitions.  
     - How to add logging using SLF4J in new components.

   - `docs/troubleshooting.md`  
     - Common runtime issues and their causes:  
       - Ask timeouts in Pekko (`AskTimeoutException`) and how to fix them (ensuring replies are sent, increasing timeouts, avoiding long blocking operations in actors).  
       - Flyway migration failures due to transactional vs non-transactional statements.  
     - Steps to debug logs, enable more verbose logging, and inspect actor / workflow state.

3. **Style and technical quality**
   - Use Markdown headings and lists for structure.  
   - Include code snippets with the correct language tags (e.g. `scala`, `sql`, `bash`).  
   - Prefer concrete file paths enclosed in backticks, for example:
     - `src/main/scala/...`
     - `src/test/scala/...`
   - Ensure commands are copy-pasteable.  
   - Do not invent technologies that are not present in the codebase.

4. **Consistency with the code**
   - Derive names of services, workflows, entities, and routes from the actual source files.  
   - When describing behavior (e.g. of `TripSearchWorkflow` or `TripSearchWorkflowService`), align with real method names and types.  
   - If something cannot be inferred, either omit it or clearly mark it as a general recommendation rather than a project-specific fact.

## Requirements

- All generated documentation must be placed under the `docs` folder.  
- Documentation must be up to date with, and consistent with, the current project state.  
- Focus on clarity, correctness, and practical usefulness for day-to-day development and operations.
