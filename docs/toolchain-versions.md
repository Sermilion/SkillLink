# Toolchain versions

Updated 2026-09-27. Use stable releases available from the publishers and verify them together. Dependencies for future CLI and database implementations are not added until those implementations need them.

## Selected versions

| Tool | Previous | Selected | Source |
| --- | --- | --- | --- |
| Build JDK | 21 | Temurin 21.0.12.1+1 | [Temurin 21 release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1) |
| Java and Kotlin bytecode target | 21 | 21 | Build tooling upgrades do not require a newer application bytecode target |
| Gradle | 8.10.2 | 9.8.0 | [Gradle release metadata](https://services.gradle.org/versions/current) |
| Kotlin and Compose compiler | 2.0.21 | 2.4.20 | [Kotlin release](https://kotlinlang.org/docs/whatsnew2420.html) |
| Compose Multiplatform | 1.7.3 | 1.12.1 | [Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) |
| JUnit | 5.13.4 | 6.1.3 | [Published BOM versions](https://repo.maven.apache.org/maven2/org/junit/junit-bom/maven-metadata.xml) |
| Spotless | 7.2.1 | 8.10.3 | [Published plugin versions](https://repo.maven.apache.org/maven2/com/diffplug/spotless/spotless-plugin-gradle/maven-metadata.xml) |
| ktlint | 1.5.0 | 1.8.0 | [Published CLI versions](https://repo.maven.apache.org/maven2/com/pinterest/ktlint/ktlint-cli/maven-metadata.xml) |
| Detekt | 1.23.8 | 1.23.8 | [Stable versions](https://repo.maven.apache.org/maven2/io/gitlab/arturbosch/detekt/detekt-gradle-plugin/maven-metadata.xml); 2.0 remains a prerelease |

The [catalog](../gradle/libs.versions.toml) owns dependency versions. The wrapper properties own the Gradle version. The build conventions select JDK 21 and target Java 21; the convention plugin's own build uses the same settings.

Kotlin documents full Gradle support through 9.7.0 and permits newer releases with possible limitations. Gradle 9.8.0 therefore requires repository verification beyond the published compatibility table. [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html), [Gradle compatibility](https://docs.gradle.org/current/userguide/compatibility.html)

Detekt 1.23.8 embeds Kotlin 2.0.21 for analysis. Passing checks on today's source does not establish support for every newer Kotlin language construct. Keep its analysis compiler isolated; do not force it to the application's Kotlin version. A JDK 27 trial failed in Detekt, first on its inferred analysis target and then inside its embedded compiler even with an explicit Java 21 target. Keep JDK 21, its documented tested JDK. Reassess Detekt and the JDK together when source begins using syntax its parser cannot understand. [Detekt compatibility](https://detekt.dev/docs/1.23.8/introduction/compatibility/), [Detekt compiler dependency](https://detekt.dev/docs/gettingstarted/gradle/)

## CI actions

All workflows retain commit pins:

| Action | Release | Commit |
| --- | --- | --- |
| `actions/checkout` | [v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | [v6.0.1](https://github.com/actions/setup-java/releases/tag/v6.0.1) | `de7274f081f381c8f8158605e0321c36c376e2e6` |
| `actions/download-artifact` | [v4.1.8](https://github.com/actions/download-artifact/releases/tag/v4.1.8) | `fa0a91b85d4f404e444e00e005971372dc801d16` |
| `actions/upload-artifact` | [v7.0.1](https://github.com/actions/upload-artifact/releases/tag/v7.0.1) | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` |
| `gradle/actions/setup-gradle` | [v6.3.0](https://github.com/gradle/actions/releases/tag/v6.3.0) | `9c971963bec38e04b3d30dcc455b5382be2fdbfb` |

The Node 24 actions require a sufficiently recent runner. The SkillLink Mac runner reported `2.337.0`, above the `2.327.1` minimum documented by setup-java. Runner compatibility does not establish that packaging passes on each host. [Setup Java requirements](https://github.com/actions/setup-java/tree/v6.0.1)

## Wrapper provenance

The wrapper scripts and JAR come from Gradle's `v9.8.0` tag. The JAR SHA-256 matches the publisher's checksum, and wrapper distribution verification remains enabled.

- Distribution SHA-256: `bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.
- Wrapper JAR SHA-256: `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`.

Sources: [distribution checksum](https://services.gradle.org/distributions/gradle-9.8.0-bin.zip.sha256), [wrapper checksum](https://services.gradle.org/distributions/gradle-9.8.0-wrapper.jar.sha256), [tagged wrapper source](https://github.com/gradle/gradle/tree/v9.8.0/gradle/wrapper).

## Verification

Verified locally on Linux with `JAVA_HOME` set to Temurin 21.0.12.1+1:

- `./gradlew check --continue --no-build-cache --parallel -q --warning-mode none` passed, including all 24 convention tests, formatting, static analysis, and dependency checks.
- `./gradlew assemble :app:createDistributable --stacktrace` passed and produced the Linux application image.
- Actionlint 1.7.12 accepted both workflows.
- `git diff --check` passed.

The Gradle 9 migration replaces the removed `ProjectDependency.dependencyProject` API with `ProjectDependency.path` in the dependency guard and its existing acceptance fixture. The tests still verify allowed and forbidden dependency edges.

The build reports Gradle 10 deprecations from Detekt and Compose hot reload. No suppressions or baselines were expanded. Configuration cache remains unverified.

The local JDK is installed at `~/.jdks/jdk-21.0.12.1+1`. Set `JAVA_HOME` to that directory to use the verified patch. CI requests the latest Temurin 21 patch through setup-java.

The application image was built, but interactive UI startup was not tested. MSI and DMG packaging still need their platform CI runs. DEB and RPM packaging were not run locally because the required packaging binaries are absent. This upgrade does not claim cross-platform installer validation.
