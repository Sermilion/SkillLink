# SL-1 subtask 1: Implement the Gradle foundation

Status: implementation complete; runtime validation is pending.

## Scope

Implement the complete build foundation specified in [the parent spec](spec.md), starting with `build-logic` and finishing with its module consumers and root lifecycle tasks. Own the wrapper, Gradle settings and scripts, catalog, convention plugins, build tests, dependency guard, quality configuration, generated-file exclusions, and corresponding documentation updates.

Read the parent spec and project-required documents before changing files. The parent owns the detailed convention contracts, module graph, compatibility selection, and validation requirements.

## Implementation sequence

1. Choose and record compatible stable dependency versions and JDK 21 setup. Add the verified wrapper, root settings, catalog, and included-build settings.
2. Implement `skilllink.jvm-library` and `skilllink.quality` in `../../../build-logic/convention`, including the convention project's own quality checks and plugin validation.
3. Add the five module build files and explicit dependency graph. Wire root `check` and `build` to include the included build and all required module tasks.
4. Add the dependency guard and behavioral fixtures for conventions, failure propagation, and forbidden dependency edges.
5. Update README, architecture status, agent instructions, enforcement inventory, and ignore rules. Record chosen versions, official compatibility sources, and any observed build limitations in `implementation-notes.md` beside this spec.

## Acceptance criteria

1. Root settings include `domain`, `application`, `infrastructure`, `desktop`, and `app`, and resolve SkillLink convention plugins from the local included build.
2. The wrapper has Unix and Windows launchers, its JAR, a pinned distribution with checksum, and documented JDK requirements; the catalog owns applicable library and plugin versions.
3. Registered JVM and quality plugins implement the parent spec's toolchain, compiler, test, formatter, static-analysis, and lazy-configuration contracts.
4. Each module declares the specified project dependencies and conventions. The dependency guard inspects production project dependencies and has acceptance and rejection fixtures for forbidden directions across compile and runtime configurations.
5. Root task wiring includes module checks, convention tests, strict plugin validation, and quality coverage for root scripts, module scripts, and build-logic source and scripts. A fixture demonstrates that a failing included-build check fails the root check.
6. Behavioral fixtures exercise real compilation, JUnit discovery, failing tests, compiler warnings, and formatting and static-analysis failures without using source-text matching to prove plugin behavior.
7. Documentation and the enforcement inventory accurately identify implemented checks, review-only principles, chosen versions, and the absence of a runnable application. No runtime implementation, desktop packaging, Skill Bill checkout dependency, or user-data access is introduced.
8. Ignore rules exclude generated output and local IDE state while preserving wrapper and future schema-export tracking; no existing user files are deleted.

## Non-goals

No product features, Compose or Room setup, additional production modules, CI or release pipeline, installer tasks, Skill Bill orchestration code, or comprehensive source architecture scanner. Do not create placeholder business classes or import reference-project configuration-cache suppressions.

## Dependency notes

No predecessor subtask. Root consumers and the included build ship together in one implementation pass. Existing SkillLink documents are authoritative. The Skill Bill reference is optional research material and must not become a build dependency.

## Validation strategy

Follow the parent's validation strategy. Use TestKit and real compile/test task outcomes for convention behavior, and Gradle dependency objects for architecture checks. Verify root `check`, root `build`, and independent build-logic `check` through the wrapper. Confirm the expected rejection fixtures and standalone-checkout behavior. Record actual commands, results, and platform limits in the workflow's validation evidence.

## Next path

Complete runtime validation for this subtask before planning Compose Desktop setup.
