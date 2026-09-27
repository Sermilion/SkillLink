![SkillLink, one canonical skill library connected to Claude, Codex, Junie, and Cursor](docs/assets/skill-link-readme-hero.svg)

# SkillLink

[![License: MIT](https://img.shields.io/badge/License-MIT-4c1.svg)](LICENSE)
[![Checks](https://github.com/Sermilion/SkillLink/actions/workflows/checks.yml/badge.svg)](https://github.com/Sermilion/SkillLink/actions/workflows/checks.yml)
![Latest release](https://img.shields.io/github/v/release/Sermilion/SkillLink?sort=semver)

Keep one copy of a skill and link it to every agent you use.

SkillLink is a local skill manager for Windows, Linux, and macOS. The first release is a CLI built on the existing Kotlin/JVM and Gradle foundation. A Compose Desktop interface for browsing and editing managed skills comes later.

See the [product idea](docs/idea.md) for the interface, installation model, usage tracking, delivery stages, and open questions.

[Quickstart](#quickstart) · [What it does](#what-it-does) · [CLI](#cli) · [Learn more](#learn-more) · [Build and packaging](#build-and-packaging)

## Quickstart

Install the latest prebuilt CLI:

```sh
curl -fsSL https://raw.githubusercontent.com/Sermilion/SkillLink/main/install.sh | bash
```

The installer downloads a platform archive when one is available and verifies its checksum. The CLI requires JDK 21 or newer at runtime.

Verify the installation:

```sh
skill-link --version
skill-link --help
```

Import a skill for selected agents:

```sh
skill-link install /path/to/my-skill/SKILL.md --agent claude --agent cursor
skill-link list
```

The original source is preserved by default. Add `--remove-original` only when source removal is explicitly intended and confirmed.

## What it does

- Explicitly import local skills into SkillLink's installation area and verify the copy.
- Show only skills managed by SkillLink.
- Manage a shared skill library and global symlinks to Claude, Codex, Junie, and Cursor.
- List managed skills with display numbers, canonical paths, and per-agent installation state.
- Use `skill-link` to install into selected agents, open a managed source for editing, and enable, disable, or remove managed skills by name.
- Detect broken managed links and block installation on case-insensitive name collisions or occupied destinations.
- Roll back interrupted installations before starting that skill's installation over.
- Remind users to restart agents after changes.

## Roadmap

- A desktop interface with a sidebar of managed skills and a main panel for reading and editing.
- Git sources and update previews.
- Recorded skill usage by agent and last use in a local SQLite database, where tracking is available.
- Trash restore and retention controls for removed skills.
- Suggestions to disable unused skills, with clear tracking coverage.

Unlinking a skill preserves its source. Usage tracking is optional, and missing tracking does not mean a skill is unused.

## CLI

The `skill-link` CLI implements:

| Command | Purpose |
| --- | --- |
| `install <SKILL.md> --agent <agent>...` | Import a selected skill and link it to the chosen agents. |
| `list` | Show managed skills, canonical paths, and per-agent installation state. |
| `open <name>` | Open a managed `SKILL.md` in the operating system's default editor. |
| `enable <name> [--agent <agent>...]` | Enable a skill for remembered or selected agents. |
| `disable <name> [--agent <agent>...]` | Disable a skill while preserving its canonical source. |
| `remove <name>` | Unlink and move a managed skill to SkillLink trash. |
| `help`, `version` | Show CLI help or the installed version. |

Management commands use case-insensitive skill names, not list row numbers. Supported agents are `claude`, `codex`, `junie`, and `cursor`. Restart affected agents after installation or content changes.

Removed bundles remain under `~/.skilllink/trash/`; restore and permanent deletion are not available.

## Installation details

The installer downloads a prebuilt CLI archive from the latest GitHub release. No git, Gradle, or build-time JDK is needed. A JDK 21+ is required at runtime to run `skill-link`.

Piped install (recommended):

```sh
curl -fsSL https://raw.githubusercontent.com/Sermilion/SkillLink/main/install.sh | bash
```

Specific release:

```sh
curl -fsSL https://raw.githubusercontent.com/Sermilion/SkillLink/main/install.sh | bash -s -- --release v1.0.0
```

From a local checkout (builds from source, requires JDK 21):

```sh
./install.sh --from-source
```

| Flag | Effect |
| --- | --- |
| `--from-source` | Build from the local checkout instead of downloading a prebuilt archive. Requires JDK 21. |
| `--local` | Same as `--from-source`. |
| `--release TAG` | Use a specific release tag for prebuilt installs. |
| `--install-dir DIR` | Override the distribution directory (default: `~/.skilllink/app`). |
| `--bin-dir DIR` | Override the launcher directory (default: `~/.local/bin`). |
| `--skip-launcher` | Install the distribution without creating launcher symlinks. |

If no prebuilt archive is available for the current platform, the installer falls back to `--from-source` automatically. After installation, verify with `skill-link --version`.

## Optional agent helper skill

The repository and CLI distribution include [`skills/skill-link-operations/SKILL.md`](skills/skill-link-operations/SKILL.md), an opt-in helper for agents that need to guide SkillLink installs and removals. It is not installed automatically and requires confirmation before every mutation.

Install it explicitly for selected agents:

```sh
skill-link install skills/skill-link-operations/SKILL.md --agent claude --agent codex
```

For an installed distribution, use `~/.skilllink/app/share/skills/skill-link-operations/SKILL.md` as the source path.

The [release workflow](.github/workflows/release.yml) builds CLI archives for linux-x64, macos-arm64, macos-x64, and windows-x64 on each tagged release.

## Learn more

- [Agent instructions](AGENTS.md) list required reading and product constraints.
- [Architecture](docs/ARCHITECTURE.md) defines hexagonal boundaries, module ownership, installation recovery, persistence, and CLI and desktop lifecycles.
- [Code principles](docs/code-principles.md) define Kotlin, Gradle, and test conventions.
- [Observability policy](docs/observability-policy.md) defines diagnostic and failure-reporting requirements.
- [CLI installation research](docs/cli-installation-research.md) records integration evidence and open design questions.
- [Toolchain versions](docs/toolchain-versions.md) records upgrade sources and verification.
- [SL-2 CLI specification](.feature-specs/done/SL-2-cli-skill-installation/spec.md) defines the command and recovery contract.

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

The build uses Gradle 9.8.0, Kotlin 2.4.20, JUnit 6.1.3, Spotless 8.10.3, ktlint 1.8.0, and Detekt 1.23.8. See [toolchain versions](docs/toolchain-versions.md) for current compatibility sources and wrapper provenance. The SL-1 [implementation notes](.feature-specs/done/SL-1-gradle-foundation/implementation-notes.md) retain the original bootstrap details. Configuration cache remains disabled pending validation.

### Continuous integration

[Checks](.github/workflows/checks.yml) runs on pull requests, pushes to `main`, and manual dispatch. GitHub-hosted Windows and Linux runners use JDK 21 and the checked-in Gradle wrapper to run `build`. This includes Spotless, Detekt, convention tests, and the module dependency guard through root `check`. Each job uploads check reports for 14 days, including when a check fails.

The workflow uses pinned action commits and a read-only repository token. Pull requests can read the Gradle cache but cannot update it.

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

[Installers](.github/workflows/packages.yml) builds Linux and Windows artifacts for pull requests and `main`. It builds the macOS DMG on the Mac mini only for `main` pushes or a manual run on `main`. Pull request code never runs on the Mac mini through these workflows. Artifacts remain available in the Actions run for 14 days; the workflow does not publish releases.

The repository runner is `skilllink-macmini`, with labels `self-hosted`, `macOS`, `ARM64`, and `skilllink`. It lives at `~/actions-runner-skilllink` on the SSH host `macmini`. Its launchd service starts when the runner user logs in. Manage it with `ssh macmini 'cd ~/actions-runner-skilllink && ./svc.sh status'`, substituting `stop` or `start` as needed. Keep the Mac awake and the runner user logged in for builds. Other projects use separate runner directories and services.

References: [Compose native packaging](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html), [Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), and [GitHub runner services](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application).

### Implementation specs

- [SL-1: Gradle project and build conventions](.feature-specs/done/SL-1-gradle-foundation/spec.md) defines the build-logic, module builds, and verification requirements.
- [SL-2: CLI skill installation and management](.feature-specs/done/SL-2-cli-skill-installation/spec.md) specifies `skill-link`, explicit agent selection, numbered listing, and management by skill name.
- [Principle enforcement inventory](docs/PrincipleEnforcementInventory.md) maps implemented checks to their proving fixtures and separates review-only rules.

## License

SkillLink is licensed under the MIT License. See [LICENSE](LICENSE).
