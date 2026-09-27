# SL-1 implementation notes

## Selected versions

| Component | Version | Compatibility basis |
| --- | --- | --- |
| Gradle wrapper | 8.10.2 | Gradle's compatibility table supports running on Java 21 from Gradle 8.5. |
| Kotlin Gradle plugin | 2.0.21 | Kotlin's compatibility table fully supports Gradle through 8.8 and lists Gradle 8.10 as supported with a multiplatform `withJava()` caveat. SkillLink targets JVM only. |
| JUnit Jupiter | 5.13.4 | Stable JUnit 5 release selected for the JVM convention and TestKit fixtures. |
| Spotless | 7.2.1 | Stable Gradle plugin release. |
| ktlint | 1.5.0 | Stable formatter release pinned in the shared catalog and passed to Spotless. |
| Detekt | 1.23.8 | Pinned stable 1.x release. Its official compatibility table recommends Gradle 8.12.1 with Kotlin 2.0.21; it requires Gradle 6.8.3 or newer. |

Compatibility references:

- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Kotlin Gradle plugin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Detekt Gradle setup and compatibility table](https://detekt.dev/docs/1.23.8/gettingstarted/gradle/)
- [JUnit 5 user guide](https://junit.org/junit5/docs/5.13.4/user-guide/)
- [Spotless 7.2.1 on the Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.diffplug.spotless/7.2.1)
- [ktlint 1.5.0 release](https://github.com/pinterest/ktlint/releases/tag/1.5.0)

Gradle 8.10.2 and Kotlin 2.0.21 are within the Kotlin Gradle plugin's documented supported range. Detekt 1.23.8 is a pinned stable 1.x release. Its table recommends a later Gradle version. The validation results below establish passing project checks for this combination on Linux.

## Wrapper and bootstrap

`gradlew`, `gradlew.bat`, and `../../../gradle/wrapper/gradle-wrapper.jar` were downloaded from the official Gradle `v8.10.2` source tag. The wrapper JAR SHA-256 is `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`, matching Gradle's [published wrapper checksum](https://gradle.org/release-checksums/). The pinned binary distribution SHA-256 is `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26`, matching the official release checksum page.

JDK 21 must be installed locally for the initial checkout. Set `JAVA_HOME` to that JDK before invoking the wrapper. Gradle uses the JDK selected by `JAVA_HOME` to run; JVM compilation selects the local Java 21 toolchain. No toolchain download resolver is configured.

The root version catalog owns Kotlin, JUnit, Spotless, ktlint, and Detekt versions. The included build imports that catalog explicitly. The wrapper distribution version and checksum live in `gradle-wrapper.properties` because they are wrapper bootstrap settings. Gradle supplies the embedded Kotlin version for Gradle Kotlin scripts. The catalog owns the Kotlin plugin version used for source compilation. Plugin resolution uses the Gradle Plugin Portal; dependencies use Maven Central. No additional repository is needed.

Ignore rules exclude nested build output, Gradle and Kotlin caches, and local IDE state. They do not match the wrapper launchers or JAR, and they do not ignore JSON files or Room schema exports.

## Quality coverage and lifecycle

Root `check` depends on every module `check` and `build-logic` `check`. The included build check reaches convention tests, strict plugin validation, formatting, and Detekt. Root Spotless checks root scripts; each module checks its own build script. The included build checks its root and settings scripts, while the convention project checks its build script, Kotlin sources, and tests. Each script has one Spotless owner. Explicit `spotlessApply` commands are `./gradlew spotlessApply` and `./gradlew -p build-logic spotlessApply`.

The repository has no runnable application entry point. `app` is an empty composition module. The build foundation adds no Compose, Room, packaging, Skill Bill checkout dependency, or user-data access.

Configuration cache is not enabled. Its support has not been established or disproved by a build run.

## Validation handoff

No build or test command ran during implementation. Validate through the checked-in wrapper, run root and independent build-logic checks, and exercise a temporary standalone copy without a sibling checkout. Record actual commands and operating systems. Keep root `build` evidence pending runtime ownership because the supplied loop has no build phase; do not substitute compilation as build evidence. Windows and macOS behavior remain unverified unless those environments are available.

## Acceptance evidence map

| Criterion | Implementation files | Behavioral or inspection evidence for validate |
| --- | --- | --- |
| AC-001 | `../../../settings.gradle.kts`, `build-logic/settings.gradle.kts`, all five module build scripts | Inspect included-build plugin resolution, module inclusion, and explicit project edges. `DependencyGuardFunctionalTest.acceptsSpecifiedGraph` exercises the permitted graph. |
| AC-002 | `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `../../../gradle/libs.versions.toml`, `README.md`, this file | Compare wrapper JAR and distribution hashes to the published checksums, inspect Unix mode `755`, catalog ownership, and JDK setup. Run wrapper commands on available operating systems. |
| AC-003 | `../../../build-logic/convention/build.gradle.kts`, convention plugin sources, `config/detekt/detekt.yml` | `JvmConventionFunctionalTest` checks target bytecode, JUnit discovery, source JAR, compiler warnings, and test failure. Quality fixtures check formatter and Detekt failures. Inspect lazy task registration and stricter validation setup. |
| AC-004 | Five module scripts, `ProductionDependencyRules.kt`, `VerifyProductionDependencies.kt` | `DependencyGuardFunctionalTest` accepts the full graph, rejects forbidden direct edges through five configuration routes, and ignores test-only edges. Confirm no artifact resolution in the guard. |
| AC-005 | Root and included-build build scripts, `Quality.kt` | `RootLifecycleFunctionalTest` proves root check reaches module checks and included-build tasks, and propagates a convention-test failure. `QualityScriptCoverageFunctionalTest` covers root, module, and included-build scripts. |
| AC-006 | Convention test sources and `TestKitSupport.kt` | Run the TestKit fixtures. Inspect their task outcomes, class files, test XML, source JAR, diagnostics, and unchanged source files. They do not match plugin source text. |
| AC-007 | `../../../README.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/code-principles.md`, `docs/PrincipleEnforcementInventory.md`, this file | Review the scope statements, check inventory, review-only rules, version sources, and absence of application behavior, packaging, Skill Bill dependencies, or user-state access. |
| AC-008 | `../../../.gitignore`, wrapper files, this file | Inspect nested build/cache and IDE exclusions, ensure wrapper files remain trackable, and confirm no JSON or schema export pattern is ignored. No existing user files were removed. |

Implementation phase evidence is limited to repository inspection, wrapper provenance and checksums, executable mode, and authored fixture/document contents. Compilation, test results, full-gate results, standalone checkout, and cross-platform behavior remain for validation. The supplied workflow has no build phase, so root `build` evidence remains pending runtime ownership.

## Completeness audit

The audit inspected AC-001 through AC-008 against the repository and authored assertions. It ran no build, test, formatter, or validation gate.

The lifecycle fixtures now copy the production settings, module scripts, included-build scripts, and plugin sources. They replace the outer TestKit suite with one real JUnit assertion to avoid recursion. The successful case checks every module guard and script formatter, included-build formatting, convention source formatting, Detekt, tests, and strict plugin validation. The failure case checks the failed included-build test task and its XML report.

The dependency acceptance fixture now copies module scripts and asserts the actual declared graph. Each rejection case checks the failed guard task and the offending source, destination, and configuration. Quality cases cover all six script locations and both production and test sources in the convention project. Failure fixtures assert task outcomes and unchanged files. JVM fixtures inspect both class targets, source entries, JUnit reports, compiler and launcher versions, and failure logging settings.

The shared TestKit runner reads its Gradle version from the wrapper properties, uses temporary TestKit directories, limits nested builds to two workers, and disables parallel and configuration-cache execution. The outer convention test task retains one fork. The repository-copy fixtures omit the outer test sources.

The audit also corrected the quality plugin's Gradle file-tree callback, aligned bootstrap Java/Kotlin targets and source JAR configuration, and added an explicit included-build identity matching root task references. The catalog now uses the Spotless and Detekt implementation artifacts for convention dependencies; plugin aliases still resolve through the Plugin Portal. The dependency task declares that it is not cacheable and inspects each configuration's own declarations while walking its parents.

The convention project cannot apply the plugins it is compiling. Its build script therefore configures JDK 21, matching targets, warnings as errors, strict nullability, JUnit Platform, one fork, failure logging, source JARs, Spotless, Detekt, and strict plugin validation directly. Detekt checks Kotlin source; Spotless checks Kotlin and Gradle Kotlin scripts.

### Test-value review

Overall verdict: Strong after repairs. The scope was the five functional-test classes and their shared fixture support. Each family catches a distinct convention or aggregation regression. Placeholder lifecycle tasks were replaced because their success did not establish the required behavior.

| Area | Verdict | Evidence | Regression protected |
| --- | --- | --- | --- |
| JVM conventions | Valuable | `JvmConventionFunctionalTest` | Mismatched targets, undiscovered tests, ignored warnings, missing sources, or swallowed test failures |
| Dependency guard | Valuable | `DependencyGuardFunctionalTest` | Missing required declarations, forbidden compile/runtime edges, or incorrect rejection of test-only dependencies |
| Kotlin quality | Valuable | `QualityConventionFunctionalTest` | Missing formatter or Detekt coverage in consumer and bootstrap source sets, or mutation during a check |
| Script quality | Valuable | `QualityScriptCoverageFunctionalTest` | An omitted root, module, included-build, or convention script target |
| Lifecycle | Valuable | `RootLifecycleFunctionalTest` | Disconnected module/included-build checks or failure to propagate a real JUnit failure |

Keep the behavioral fixture families. No deletion candidates or further rewrites remain after these repairs. Runtime results, standalone wrapper execution, and unavailable-platform evidence remain with validation.

## Validation results

Validation ran on Linux x86_64 with OpenJDK 21.0.11 and the checked-in Gradle 8.10.2 wrapper. A temporary standalone copy at `/tmp/skilllink-sl1-validation-lj8g2pjf` contained the tracked repository files, including the working-tree fixture repair. Commands used `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` and `PATH=/usr/bin:/bin`, where no Skill Bill executable was available. The build required no sibling checkout. Gradle dependency and task caches remained available.

| Command | Result |
| --- | --- |
| `./gradlew --version` | Passed. Reported Gradle 8.10.2 and JDK 21.0.11 on Linux. |
| `./gradlew projects --console=plain` | Passed. Listed all five modules and the local `build-logic` included build. |
| `./gradlew -p build-logic :convention:test --tests skilllink.buildlogic.RootLifecycleFunctionalTest --console=plain` | Passed after the fixture repair. Both lifecycle tests passed. |
| `./gradlew check --console=plain` | Passed after the fixture repair. All 24 functional tests passed with zero failures, errors, or skipped tests. Module checks, dependency guards, formatting, Detekt, and strict plugin validation passed. |
| `./gradlew -p build-logic check --console=plain` | Passed independently. Gradle reused the passing test and quality outputs from the root check as up to date. |
| `git diff --check` | Passed in the workspace. |

The first root check reported 22 passing tests and two lifecycle failures. The lifecycle helper generated an extra trailing blank line in its Kotlin test and appended build-script text. Spotless rejected the generated test before the intended lifecycle assertion. Removing those extra blank lines fixed the fixtures. The focused lifecycle rerun passed, followed by the complete root check. No assertions, quality rules, or test coverage were weakened.

JUnit reports contain seven dependency-guard cases, three JVM convention cases, six Kotlin quality cases, six script quality cases, and two lifecycle cases. The expected compiler, JUnit, formatting, static-analysis, dependency, and included-build failures passed as regression tests.

Repository inspection confirmed the wrapper JAR SHA-256 recorded above and Unix mode `755`. `git check-ignore` confirmed nested build output, Gradle/Kotlin caches, and IDE exclusions. The wrapper launchers, wrapper JAR, and a future `infrastructure/schemas/skilllink/1.json` export remain trackable. No existing user files were deleted.

Logs remain in `/tmp/skilllink-sl1-root-check.log`, `/tmp/skilllink-sl1-lifecycle-recheck.log`, `/tmp/skilllink-sl1-root-recheck.log`, and `/tmp/skilllink-sl1-build-logic-check.log`. Final JUnit XML and HTML reports are under the standalone copy's `../../../build-logic/convention/build` directory.

No required validation checks remain failing. Windows and macOS execution remain unverified. Configuration-cache support remains unverified and disabled by default. Root `build` was not run because this runtime phase does not own build execution. No pack validation command or repository-root checklist ran.
