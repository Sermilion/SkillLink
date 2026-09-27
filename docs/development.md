# Development and releases

This guide covers local builds, CI, release publishing, desktop packaging, and implementation references. The [README](../README.md) contains user-facing installation and CLI documentation.

## Build and packaging

Run the CLI through `./gradlew :app:runSkillLinkCli --args="--help"`. Build launcher scripts with `./gradlew :app:skillLinkCliDistribution` (outputs under `app/build/skill-link-cli/`). JDK 21 is required.

### Build setup

Install JDK 21 and set `JAVA_HOME` to that JDK. The wrapper starts Gradle from the repository and the JVM convention selects a local JDK 21 toolchain. The build does not install a missing JDK.

Use the checked-in wrapper for build work:

```sh
./gradlew projects
./gradlew check
./gradlew build
./gradlew -p build-logic check
```

On Windows, run the same tasks through `gradlew.bat`. `check` covers all module checks and the included build. Format root and module files with `./gradlew spotlessApply`; format build-logic files with `./gradlew -p build-logic spotlessApply`.

The build uses Gradle 9.8.0, Kotlin 2.4.20, JUnit 6.1.3, Spotless 8.10.3, ktlint 1.8.0, and Detekt 1.23.8. See [toolchain versions](toolchain-versions.md) for current compatibility sources and wrapper provenance. The SL-1 [implementation notes](../.feature-specs/done/SL-1-gradle-foundation/implementation-notes.md) retain the original bootstrap details. Configuration cache remains disabled pending validation.

### Continuous integration

The [checks workflow](../.github/workflows/checks.yml) runs on pull requests, pushes to `main`, and manual dispatch. GitHub-hosted Windows and Linux runners, plus the self-hosted macOS ARM64 runner, use JDK 21 and the checked-in Gradle wrapper to run `build`. This includes Spotless, Detekt, convention tests, and the module dependency guard through root `check`. Each job uploads check reports for 14 days, including when a check fails.

The workflow uses pinned action commits and a read-only repository token. Pull requests can read the Gradle cache but cannot update it.

### CLI releases

[Release](../.github/workflows/release.yml) builds and checksum-verifies CLI archives for Linux x64, macOS, and Windows x64. Push a `vX.Y.Z` tag to publish a GitHub release after all three builds and the release check gate pass:

```sh
git tag v0.1.0
git push origin v0.1.0
```

Maintainers can manually stage a downloadable prerelease from the Actions tab with a version such as `v0.1.0-staging.1`. Staging runs cannot use a stable version. Release assets are retained as workflow artifacts for seven days and published with SHA-256 sidecars.

### Desktop shell and native installers

The desktop shell and CLI have separate entry points. Use `:app:run` for the desktop shell, `:app:runSkillLinkCli` for the CLI, and `:app:skillLinkCliDistribution` for the standalone CLI scripts.

Run the shell with `./gradlew :app:run`. Compose Multiplatform 1.12.1 uses the Kotlin 2.4.20 compiler plugin and Material 3. Google Maven supplies the AndroidX dependencies required by Compose; other libraries continue to resolve from Maven Central. Spotless and Detekt allow the standard uppercase naming for functions annotated with `@Composable`.

Build native installers on the matching operating system with Temurin JDK 21. Compose rejects Homebrew's JDK for macOS packaging; CI installs Temurin with `actions/setup-java`.

| Host | Command | Output directory |
| --- | --- | --- |
| Linux x64 | `./gradlew :app:packageDeb :app:packageRpm` | `app/build/compose/binaries/main/deb/` and `rpm/` |
| Windows x64 | `gradlew.bat :app:packageMsi` | `app/build/compose/binaries/main/msi/` |
| macOS ARM64 | `./gradlew :app:packageDmg` | `app/build/compose/binaries/main/dmg/` |

Linux packaging needs `rpm` and `fakeroot`. The Windows CI image supplies WiX. Installers bundle their own Java runtime. Version `1.0.0` is the initial packaging version, not a claim that the planned product is complete. These development installers are unsigned; macOS signing/notarization and Windows signing require separate certificate setup. Packaging never reads or removes `~/.skilllink/`.

The [installers workflow](../.github/workflows/packages.yml) builds DEB, RPM, MSI, and DMG packages only when a `vX.Y.Z` release tag is pushed. The packages remain available as Actions artifacts for 14 days. The workflow does not publish them to the GitHub release.

The repository runner is `skilllink-macmini`, with labels `self-hosted`, `macOS`, `ARM64`, and `skilllink`. It lives at `~/actions-runner-skilllink` on the SSH host `macmini`. Its launchd service starts when the runner user logs in. Manage it with `ssh macmini 'cd ~/actions-runner-skilllink && ./svc.sh status'`, substituting `stop` or `start` as needed. Keep the Mac awake and the runner user logged in for builds. Other projects use separate runner directories and services.

References: [Compose native packaging](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html), [Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), and [GitHub runner services](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application).