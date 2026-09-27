## [2026-09-27] SL-2 CLI skill installation and listing
Areas: app, application, build-logic, cli, docs, domain, infrastructure, gradle

- Added the `skill-link` CLI entry point and distribution tasks for install, list, help, and version, with lazy runtime wiring so help and version do not initialize SQLite.
- Added domain rules for skill names, front matter, and source bundles, plus application install, listing, and recovery operations behind ports.
- Added SQLite catalog and journal persistence, native filesystem and agent adapters, writer locking, diagnostics, CLI parsing and rendering, and dependency-guard coverage for the new module graph.
- Documented the ownership and recovery protocol and updated the architecture and enforcement inventory. Management commands and later desktop behavior remain outside this subtask.
Feature flag: N/A

## [2026-09-27] SL-1 Gradle foundation
Areas: build-logic, root Gradle build, gradle, config/detekt, domain, application, infrastructure, desktop, app, docs

- Added the Gradle 8.10.2 wrapper and shared version catalog for a standalone checkout with a locally installed JDK 21.
- Added reusable `skilllink.jvm-library` and `skilllink.quality` plugins for matching Java/Kotlin targets, warnings as errors, strict nullability, JUnit Platform, source JARs, Spotless, and Detekt.
- Applied conventions and explicit production dependencies to all five modules. The convention project configures its own settings directly because it cannot apply plugins it is compiling.
- Added a reusable dependency guard over direct production compile/runtime project declarations, including inherited configurations. Test-only dependencies remain outside its scope.
- Wired root `check` to module and included-build checks, and root `build` to module builds and root checks. Each authored Gradle script has one Spotless owner; formatting checks leave files unchanged.
- Added behavioral TestKit fixtures for compiler, JUnit, quality, dependency, and included-build failure propagation. The enforcement inventory separates automated checks from review-only rules.
- The modules remain empty application boundaries. Compose, Room, installers, and a runnable entry point remain future work; source import and other architecture scans remain review-only.
- Windows/macOS execution, root `build` execution, and configuration-cache support remain unverified. Configuration cache stays disabled.
Feature flag: N/A
Acceptance criteria: 8/8 implemented for subtask 1
