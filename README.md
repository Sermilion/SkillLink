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

The desktop shell opens a two-panel library window. Skill import, editing, agent links, and persistence are not implemented yet.

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

## Continuous integration

[Checks](.github/workflows/checks.yml) runs on pull requests, pushes to `main`, and manual dispatch. GitHub-hosted Windows and Linux runners use JDK 21 and the checked-in Gradle wrapper to run `build`. This includes Spotless, Detekt, convention tests, and the module dependency guard through root `check`. Each job uploads check reports for 14 days, including when a check fails.

The workflow uses pinned action commits and a read-only repository token. Pull requests can read the Gradle cache but cannot update it.

## Desktop and installers

Run the shell with `./gradlew :app:run`. Compose Multiplatform 1.7.3 uses the existing Kotlin 2.0.21 compiler plugin and Material 3. Google Maven supplies the AndroidX dependencies required by Compose; other libraries continue to resolve from Maven Central. Spotless and Detekt allow the standard uppercase naming for functions annotated with `@Composable`.

Build native installers on the matching operating system with JDK 21:

| Host | Command | Output directory |
| --- | --- | --- |
| Linux x64 | `./gradlew :app:packageDeb :app:packageRpm` | `app/build/compose/binaries/main/deb/` and `rpm/` |
| Windows x64 | `gradlew.bat :app:packageMsi` | `app/build/compose/binaries/main/msi/` |
| macOS ARM64 | `./gradlew :app:packageDmg` | `app/build/compose/binaries/main/dmg/` |

Linux packaging needs `rpm` and `fakeroot`. The Windows CI image supplies WiX. Installers bundle their own Java runtime. Version `1.0.0` is the initial packaging version, not a claim that the planned product is complete. These development installers are unsigned; macOS signing/notarization and Windows signing require separate certificate setup. Packaging never reads or removes `~/.skilllink/`.

[Installers](.github/workflows/packages.yml) builds Linux and Windows artifacts for pull requests and `main`. It builds the macOS DMG on the Mac mini only for `main` pushes or a manual run on `main`. Pull request code never runs on the Mac mini through these workflows. Artifacts remain available in the Actions run for 14 days; the workflow does not publish releases.

The repository runner is `skilllink-macmini`, with labels `self-hosted`, `macOS`, `ARM64`, and `skilllink`. It lives at `~/actions-runner-skilllink` on the SSH host `macmini`. Its launchd service starts when the runner user logs in. Manage it with `ssh macmini 'cd ~/actions-runner-skilllink && ./svc.sh status'`, substituting `stop` or `start` as needed. Keep the Mac awake and the runner user logged in for builds. Other projects use separate runner directories and services.

References: [Compose native packaging](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html), [Compose 1.7.3](https://kotlinlang.org/docs/multiplatform/whats-new-compose-170.html), and [GitHub runner services](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application).

## Implementation specs

- [SL-1: Gradle project and build conventions](.feature-specs/SL-1-gradle-foundation/spec.md) defines the build-logic, module builds, and verification requirements.
- [Principle enforcement inventory](docs/PrincipleEnforcementInventory.md) maps implemented checks to their proving fixtures and separates review-only rules.
