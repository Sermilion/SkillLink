![SkillLink, one canonical skill library connected to Claude, Codex, Junie, and Cursor](docs/assets/skill-link-readme-hero.svg)

# SkillLink

[![License: MIT](https://img.shields.io/badge/License-MIT-4c1.svg)](LICENSE)
[![Checks](https://github.com/Sermilion/SkillLink/actions/workflows/checks.yml/badge.svg)](https://github.com/Sermilion/SkillLink/actions/workflows/checks.yml)
![Latest release](https://img.shields.io/github/v/release/Sermilion/SkillLink?sort=semver)

Keep one copy of a skill and link it to every agent you use.

SkillLink is a local skill manager for Windows, Linux, and macOS. The first release is a CLI built on the existing Kotlin/JVM and Gradle foundation. A Compose Desktop interface for browsing and editing managed skills comes later.

See the [product idea](docs/idea.md) for the interface, installation model, usage tracking, delivery stages, and open questions.

[Quickstart](#quickstart) · [What it does](#what-it-does) · [CLI](#cli) · [Learn more](#learn-more)

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

## Learn more

- [Product idea](docs/idea.md) explains the installation model, interface, roadmap, and open questions.
- [Development and releases](docs/development.md) covers builds, CI, release publishing, packaging, and implementation references.

## License

SkillLink is licensed under the MIT License. See [LICENSE](LICENSE).
