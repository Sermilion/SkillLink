# Principle enforcement inventory

This inventory records implemented build checks and rules that still need review. The Gradle test fixtures are authored as `*FunctionalTest` classes under `build-logic/convention/src/test/kotlin/skilllink/buildlogic`. Fixture execution belongs to validation; this inventory does not claim passing results.

## Automated checks

| Rule | Checking task | Proving fixture | Coverage |
| --- | --- | --- | --- |
| Java and Kotlin target JDK 21; Kotlin rejects warnings; tests use JUnit Platform and one fork | `compileJava`, `compileKotlin`, and `test` through module `check`; `sourcesJar` | `JvmConventionFunctionalTest.compilesJavaAndKotlinForJava21AndRunsJUnit`, `rejectsCompilerWarning`, and `failsOnJUnitFailure` | Class-file targets, Java compiler and test launcher versions, JUnit XML, both source JAR entries, strict-nullability arguments, logging settings, compiler-warning rejection, and a failing assertion |
| Authored Kotlin passes formatting and static analysis | Module and convention `spotlessKotlinCheck` and `detekt` through `check` | `QualityConventionFunctionalTest.rejectsFormattingViolation` and `rejectsStaticAnalysisViolation` | Separate failures identify the failed task and offending file. Checks leave files unchanged. Cases cover a consumer module and build-plugin production and test sources. Wildcard imports are rejected in all of these locations. |
| Gradle Kotlin scripts pass formatting | Root, module, included-build root, and convention `spotlessKotlinGradleCheck` through `check` | `QualityScriptCoverageFunctionalTest.rejectsFormattingViolationsInBuildScripts` | Six cases cover root build/settings, a module build, included-build build/settings, and convention build scripts. Each script has one Spotless owner. Detekt checks Kotlin sources, not these scripts. |
| Direct production project dependencies follow the module graph | Module `verifyProductionDependencies`, wired to module `check` | `DependencyGuardFunctionalTest.acceptsSpecifiedGraph`, `rejectsForbiddenDirectEdgesAcrossProductionConfigurations`, and `ignoresTestOnlyProjectDependencies` | The acceptance fixture copies production module scripts and asserts their declared graph. Rejection cases cover `implementation`, `api`, `compileOnly`, `runtimeOnly`, and a custom configuration inherited by the runtime classpath. The guard reads dependency objects without resolving artifacts. |
| Root `check` reaches every module and the included-build checks | Root `check` | `RootLifecycleFunctionalTest.rootCheckReachesModulesTestsPluginValidationAndQualityTasks` and `propagatesIncludedBuildTestFailure` | Fixtures copy production settings, module scripts, build-logic scripts, and plugin source. A single real JUnit test replaces the outer TestKit suite to prevent recursion. Assertions check task outcomes and JUnit XML, including a deliberate assertion failure. |
| Gradle plugins use stricter validation | Convention `validatePlugins`, wired to included-build `check` | `RootLifecycleFunctionalTest.rootCheckReachesModulesTestsPluginValidationAndQualityTasks` | The real validation task must succeed and report `enableStricterValidation=true`. |

## Repository inspection

These requirements have inspection evidence rather than dedicated regression tests:

- Wrapper launchers, JAR, executable permission, distribution checksum, JDK instructions, and catalog version ownership.
- Lazy task registration and configuration, settings-owned repositories, and the absence of filesystem capability probes.
- Generated-output and IDE ignore patterns, wrapper and schema-export retention, and preservation of existing user files.
- Documentation accuracy and the absence of application behavior, packaging, Skill Bill dependencies, and user-data access.

## Review-only requirements

- Domain and application code must not depend on UI, filesystem, SQL, or operating-system APIs.
- Source imports, Kotlin comments, inline fully qualified names, package/file ceilings, and wire-key ownership are not scanned.
- Installation recovery, cancellation, transaction ownership, privacy, and observability are not implemented or mechanically checked by this foundation.
- No task proves Windows or macOS wrapper behavior or a runnable application. Standalone wrapper execution and platform results require the later validation evidence described in the implementation notes.

The dependency guard covers direct production project declarations on compile and runtime classpath configuration hierarchies. It does not reject allowed transitive reachability, inspect external modules, or enforce the full hexagonal architecture.
