# SL-1: Gradle project and build conventions

Status: implementation complete; runtime validation is pending.
Mode: `single_spec`, with one executable subtask.

## Outcome

Create SkillLink's Gradle project at the repository root. Start with an included `build-logic` build that owns shared JVM and quality conventions, then apply those conventions to the five modules defined in `../../../docs/ARCHITECTURE.md`.

A contributor should be able to use the checked-in wrapper to compile the project and check both application modules and build logic from a standalone checkout. No sibling Skill Bill checkout or installed Skill Bill runtime may be required by the build.

## Scope

- Gradle wrapper for Unix and Windows, settings, root lifecycle tasks, and a shared version catalog.
- An included `build-logic` build with a `convention` plugin project.
- `skilllink.jvm-library` and `skilllink.quality` convention plugins.
- Build files for `domain`, `application`, `infrastructure`, `desktop`, and `app`, with explicit dependencies matching the architecture.
- Behavioral tests of convention application, build failure propagation, and module dependency restrictions.
- Contributor commands, generated-file exclusions, and accurate enforcement documentation.

The five module builds establish boundaries for later features. Do not add placeholder business types, a fake application entry point, or empty source packages to make compilation appear meaningful. The convention tests supply real compilation and test fixtures.

## Required reading

- `../../../AGENTS.md`
- `../../../docs/idea.md`
- `../../../docs/ARCHITECTURE.md`
- `../../../docs/code-principles.md`
- `../../../docs/observability-policy.md`

## Reference and adaptation

The reviewed reference is Skill Bill commit `775a1d470`, under `runtime-kotlin/build-logic`. Its included build has a `convention` project, imports the parent version catalog, registers binary Gradle plugins, and shares JVM configuration through `Jvm.kt`. `Quality.kt` configures Spotless and Detekt. Its root build-logic `check` delegates to `:convention:check`.

Useful reference files:

- `runtime-kotlin/settings.gradle.kts`
- `runtime-kotlin/gradle/libs.versions.toml`
- `runtime-kotlin/build-logic/settings.gradle.kts`
- `runtime-kotlin/build-logic/build.gradle.kts`
- `runtime-kotlin/build-logic/convention/build.gradle.kts`
- `runtime-kotlin/build-logic/convention/src/main/kotlin/dev/skillbill/runtime/buildlogic/JvmLibraryConventionPlugin.kt`
- `runtime-kotlin/build-logic/convention/src/main/kotlin/dev/skillbill/runtime/buildlogic/Jvm.kt`
- `runtime-kotlin/build-logic/convention/src/main/kotlin/dev/skillbill/runtime/buildlogic/QualityConventionPlugin.kt`
- `runtime-kotlin/build-logic/convention/src/main/kotlin/dev/skillbill/runtime/buildlogic/Quality.kt`

These references informed this spec. The requirements below are sufficient without that checkout. Use `skilllink.buildlogic` for new build-plugin code. Do not copy Skill Bill environment gates, snapshot controls, workflow resources, runtime-image plugins, version derivation, or its configuration-cache exemptions. Its current catalog uses a beta Kotlin version, which is not a requirement for SkillLink.

## Build layout

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
gradlew
gradlew.bat
gradle/
  libs.versions.toml
  wrapper/
    gradle-wrapper.jar
    gradle-wrapper.properties
build-logic/
  settings.gradle.kts
  build.gradle.kts
  convention/
    build.gradle.kts
    src/main/kotlin/skilllink/buildlogic/
    src/test/kotlin/skilllink/buildlogic/
config/detekt/detekt.yml
domain/build.gradle.kts
application/build.gradle.kts
infrastructure/build.gradle.kts
desktop/build.gradle.kts
app/build.gradle.kts
```

Use `rootProject.name = "skilllink"` and `pluginManagement { includeBuild("build-logic") }`. The included build imports `../gradle/libs.versions.toml` explicitly. Keep dependency repositories in settings and reject project-level repositories in both builds. Use Maven Central for libraries and the Gradle Plugin Portal where plugin resolution requires it. Add another repository only for a concrete dependency with a documented reason.

Pin a compatible stable Gradle, Kotlin, JUnit, Spotless, ktlint, and Detekt set during implementation, after consulting their official compatibility documentation. Record the chosen versions and compatibility sources in the implementation notes. Do not use dynamic versions, snapshots, or pre-releases by default. The wrapper must have the official distribution checksum. Commit its JAR and both launchers, with the Unix executable bit set.

Use JDK 21 for the project toolchain and JVM bytecode target. Align Java and Kotlin compilation. Document the JDK needed to launch the selected Gradle version and how the toolchain is provisioned. Do not silently depend on a developer's pre-existing Gradle installation. Third-party dependency and plugin versions belong in the shared catalog where Gradle supports catalog access. Wrapper and settings-only plugin versions may live in their required bootstrap locations; document those exceptions instead of maintaining duplicate version declarations.

## Convention contracts

### JVM library

`skilllink.jvm-library` applies Kotlin JVM and `java-library`. It configures the shared toolchain, matching Java/Kotlin targets, Kotlin warnings as errors, strict supported nullability handling, source JARs, and JUnit Platform test execution. Provide the shared JUnit dependencies so consumers do not repeat them. Keep optional libraries such as coroutines out of this baseline until a module needs them.

Use lazy task configuration. Keep test concurrency bounded with a conservative default and show failure causes and stack traces. Do not inherit Skill Bill's eight-fork and two-gigabyte defaults without a measured need. Avoid `afterEvaluate`, cross-project mutation, and probing for source directories to decide capabilities. Apply conventions explicitly even when a module has no sources yet.

### Quality

`skilllink.quality` applies Spotless and Detekt, with a pinned ktlint formatter and a shared Detekt configuration. Include authored Kotlin and Gradle Kotlin scripts in the appropriate checks. Cover root scripts and build-logic scripts as well as module scripts. Exclude generated build output, without excluding authored code or creating suppression baselines.

Formatting checks report failures; only an explicit formatting task rewrites files. Detekt is configured for Kotlin sources where present. Root and build-logic aggregation must not accidentally leave build-plugin source outside the quality gate. Bootstrap the convention project with direct configuration where it cannot apply the plugin it is compiling, keeping the settings aligned and the reason documented.

Enable strict Gradle plugin validation. A successful root `check` must include convention tests, plugin validation, formatting, static analysis, and each module's checks. Included-build checks require explicit wiring; do not assume Gradle includes them automatically. Root `build` aggregates application-module builds and the root checks without introducing a task dependency cycle.

## Module dependencies

Use explicit `implementation(project(...))` dependencies initially. Add an `api` edge only when a real exported signature requires it and document that reason.

| Module | Direct production project dependencies |
| --- | --- |
| `domain` | None |
| `application` | `domain` |
| `infrastructure` | `application`, `domain` |
| `desktop` | `application`, `domain` |
| `app` | `desktop`, `infrastructure`, `application` |

All five initially apply the JVM and quality conventions. `app` is the reserved composition and packaging module but does not expose a runnable task until it has a real entry point. `desktop` remains a JVM module ready for later Compose integration. Do not apply Android, JS, or native target plugins.

Add a narrow dependency guard using Gradle's project dependency model. It must reject a forbidden edge on production compile or runtime paths, including an attempt to bypass it through `api`, `compileOnly`, or `runtimeOnly`. Build fixtures must cover an allowed graph and at least an outward core dependency and a desktop-to-infrastructure dependency. Do not inspect build-script text to infer the graph.

Pair this guard with its proving test in one `PrincipleEnforcementInventory`. Document the guard's exact coverage. Framework import restrictions, Kotlin comment scans, file/package ceilings, and wire-key scans remain review-only until their own guards exist. Do not claim full hexagonal enforcement from a project-dependency guard.

## Acceptance criteria

1. The repository contains a root Gradle build, checked-in wrapper with distribution checksum, shared catalog, and explicit settings for the five target modules and the included `build-logic` build.
2. `../../../build-logic/convention` registers `skilllink.jvm-library` and `skilllink.quality`, and their implementations own the shared JVM, test, formatting, and analysis settings described above.
3. Module build files declare only the permitted production project dependencies and apply their conventions explicitly without filesystem-based capability detection.
4. Root lifecycle task wiring includes module checks, included-build tests, plugin validation, and quality checks covering authored Kotlin and Gradle scripts. Root `build` includes those checks and module builds.
5. Behavioral test fixtures demonstrate JVM target alignment, warnings-as-errors rejection, JUnit discovery and failing-test propagation, quality failure propagation, and included-build failure propagation to root `check`.
6. A project-dependency guard, its acceptance and rejection fixtures, and an enforcement inventory cover the allowed module graph and prohibited production edges.
7. Build files contain no absolute reference to Skill Bill, no Skill Bill runtime dependency, no user-state access, and no copied workflow or runtime-image conventions.
8. README documents the actual build, check, and formatting commands, JDK setup, and the fact that this foundation has no runnable desktop app. Architecture and agent instructions describe the implemented build and the precise remaining enforcement gaps.
9. Version-control exclusions cover generated outputs and local IDE state while retaining the wrapper JAR and allowing future versioned Room schema exports.

## Constraints and non-goals

This task does not implement skill import, filesystem links, local database storage, activity tracking, UI screens, Compose integration, or native installers. Room, KSP, Compose, Material 3, application signing, release version derivation, CI, and distribution conventions belong to later work with real consumers.

Preserve existing documentation and local IDE files. Adding ignore rules must not delete user files. Build fixtures use temporary directories and must not inspect or mutate `~/.skilllink`, `~/.skill-bill`, or agent installations. Follow the authored Kotlin comment policy in build scripts, plugin code, and tests.

Configuration-cache support must be assessed for the chosen versions. Do not enable it by default without validation or copy a reference exemption. If unsupported, document the observed limitation and leave it disabled rather than claim compatibility.

## Validation strategy

Use the checked-in wrapper for all build validation. Intended commands after implementation:

```sh
./gradlew --version
./gradlew projects
./gradlew check
./gradlew build
./gradlew -p build-logic check
```

Run the equivalent `gradlew.bat` commands on Windows when a Windows environment is available. Record the operating system actually validated; a Linux run does not establish Windows or macOS compatibility.

Use Gradle TestKit fixtures that compile actual Kotlin/Java, execute a real JUnit test, and produce expected failures. Name the regression each test protects. Check task outcomes and diagnostics, not plugin source strings. Keep failure fixtures out of normal production sources.

A deliberately failing included-build test must make a fixture root `check` fail. A formatting violation and static-analysis violation must each fail the relevant quality gate. Use a source fixture with a compiler warning to establish warnings-as-errors behavior. Inspect compiled bytecode or compile-task properties for target alignment, with compilation as evidence that the settings are usable.

Check the final build in a temporary standalone checkout without access to the reference repository or Skill Bill executable. Dependency downloads may require network access. Record failures honestly and distinguish unavailable platform environments from passing checks.

## Delivery and dependency notes

One subtask delivers the build foundation as a coherent change. It has no prior implementation dependency. Splitting wrapper, conventions, and consumers would leave intermediate work without a useful project build.

See [the executable subtask](spec_subtask_1_gradle_foundation.md) and [implementation notes](implementation-notes.md). Runtime validation remains outstanding.

## Next path

Complete runtime validation for the Gradle foundation before adding Compose Desktop and packaging conventions to `desktop` and `app`.
