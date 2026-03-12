# Project: api-producer (Spring Boot Microservice)

## Security Rules

- **NEVER read, display, or output the contents of `.env`, `.env.*`, or any environment variable files.**
- **NEVER read or display files containing secrets, credentials, API keys, tokens, or passwords** (e.g., `application-secrets.yml`, `credentials.json`, `*.key`, `*.pem`, `*.p12`, `*.jks`).
- **NEVER output sensitive values** from `application.properties`, `application.yml`, or any Spring profile configuration that contains passwords, tokens, or connection strings. If you need to reference these files, redact any sensitive values.
- **NEVER commit `.env` files or secrets to git.** Warn the user if they attempt to do so.
- When editing configuration files (`application.properties`, `application.yml`, `compose.yaml`), use placeholder values (e.g., `${DB_PASSWORD}`, `changeme`) instead of real credentials.
- If a task requires knowledge of a secret value, ask the user to provide it at runtime rather than hardcoding it.

## Project Conventions

- This is a Spring Boot microservice using Maven.
- Source code is in `src/main/java` and tests in `src/test/java`.
- Use the existing project structure and conventions when adding new code.
