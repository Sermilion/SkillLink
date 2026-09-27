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

The architecture describes the target implementation. Application modules and automated guards have not been created yet.

## Implementation specs

- [SL-1: Gradle project and build conventions](.feature-specs/SL-1-gradle-foundation/spec.md) defines the initial build-logic, module builds, and verification requirements. Implementation is pending.
