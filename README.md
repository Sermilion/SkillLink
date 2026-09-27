# SkillLink

Keep one copy of a skill and link it to every agent you use.

SkillLink is a desktop skill manager for Windows, Linux, and macOS, using Kotlin, Gradle, and Compose Desktop, as Skill Bill's former desktop app did. It has a sidebar for managed skills and a main panel for viewing and editing their content.

See the [product idea](docs/idea.md) for the interface, installation model, usage tracking, delivery stages, and open questions.

## Initial scope

- Explicitly import local skills into SkillLink's installation area, verify the copy, and remove the original after installation commits.
- Show only skills managed by SkillLink.
- Manage a shared skill library and global symlinks to Claude, Codex, Junie, and Cursor.
- Browse and edit skill content.
- Detect broken managed links and block installation on case-insensitive name collisions or occupied destinations.
- Roll back interrupted installations before starting that skill's installation over.
- Remind users to restart agents after changes.

## Later

- Git sources and update previews.
- Recorded skill usage by agent and last use in a local SQLite database, where tracking is available.
- Trash and undo for removed skills.
- Suggestions to disable unused skills, with clear tracking coverage.

Unlinking a skill preserves its source. Usage tracking is optional, and missing tracking does not mean a skill is unused.

## Development documents

- [Agent instructions](AGENTS.md) list required reading and product constraints.
- [Architecture](docs/ARCHITECTURE.md) defines hexagonal boundaries, module ownership, installation recovery, persistence, and desktop lifecycle.
- [Code principles](docs/code-principles.md) define Kotlin, Gradle, and test conventions.
- [Observability policy](docs/observability-policy.md) defines diagnostic and failure-reporting requirements.

The Gradle foundation is in place. The five modules are empty boundaries, and the repository has no runnable desktop application yet.

## Build setup

Install JDK 21 and set `JAVA_HOME` to that JDK. The wrapper starts Gradle from the repository and the JVM convention selects a local JDK 21 toolchain. The build does not install a missing JDK.

Use the checked-in wrapper for build work:

```sh
./gradlew projects
./gradlew check
./gradlew build
./gradlew -p build-logic check
```

On Windows, run the same tasks through `gradlew.bat`. `check` covers all module checks and the included build. Format root and module files with `./gradlew spotlessApply`; format build-logic files with `./gradlew -p build-logic spotlessApply`.

The build uses Gradle 8.10.2, Kotlin 2.0.21, JUnit 5.13.4, Spotless 7.2.1, ktlint 1.5.0, and Detekt 1.23.8. See [implementation notes](.feature-specs/SL-1-gradle-foundation/implementation-notes.md) for compatibility sources, wrapper provenance, and bootstrap details. Configuration cache remains disabled pending validation.

## Implementation specs

- [SL-1: Gradle project and build conventions](.feature-specs/SL-1-gradle-foundation/spec.md) defines the build-logic, module builds, and verification requirements.
- [Principle enforcement inventory](docs/PrincipleEnforcementInventory.md) maps implemented checks to their proving fixtures and separates review-only rules.
