# CLI installation research

Research date: 2026-09-27. This is an initial evidence record, not an implementation spec or a set of accepted decisions. [The product idea](idea.md) and [architecture](ARCHITECTURE.md) remain authoritative.

## What we have

At the start of this investigation, SkillLink had five Gradle modules, Kotlin 2.0.21, JDK 21, Gradle 8.10.2, build conventions, and a Compose shell. See [toolchain versions](toolchain-versions.md) for the subsequent upgrade. The production application currently opens a window. The domain, application, and infrastructure modules have no implementation source yet. There is no CLI parser, database adapter, installation journal, or recovery implementation.

Evidence: [version catalog](../gradle/libs.versions.toml), [module declarations](../settings.gradle.kts), [application build](../app/build.gradle.kts), and [entry point](../app/src/main/kotlin/skilllink/app/Main.kt).

The [dependency guard](../build-logic/convention/src/main/kotlin/skilllink/buildlogic/ProductionDependencyRules.kt) enforces allowed direct project dependencies. It does not stop application code from importing Java filesystem APIs or enforce resource ownership. Those boundaries still need review. Adding a CLI module would require an explicit graph and guard update.

## What Skill Bill establishes

The inspected sibling checkout was clean at `b6fa1ae2a322b6b6addb1d018e867f92d5698f48`. Historical sources were read through Git without checking out another revision or running an installer.

### Early installation model

The [installer at 0ee27b14b](https://github.com/Sermilion/skill-bill/blob/0ee27b14b2e47cd8cfab2827bf9b59e72bf15d4e/install.sh) links the primary agent to skill folders in the checkout. Secondary agents link through that primary agent. It enumerates the bundled skills and removes occupied destinations before linking. It supports Copilot, Claude, GLM, and Codex, a different set from SkillLink's first release.

The useful principle is one source shared through symlinks. SkillLink already requires direct links to an independent managed library, explicit import, collision rejection, and recovery after interruption. The old script does not establish those guarantees.

### Current integration behavior

At the inspected revision, the shell delegates path resolution to Kotlin. The relevant sources live under `runtime-kotlin/runtime-infra/skills/src/main/kotlin/skillbill/infrastructure/skills/install/`:

- [InstallPrimitives.kt](https://github.com/Sermilion/skill-bill/blob/b6fa1ae2a322b6b6addb1d018e867f92d5698f48/runtime-kotlin/runtime-infra/skills/src/main/kotlin/skillbill/infrastructure/skills/install/plan/InstallPrimitives.kt) maps agents to skill locations.
- [CodexConfigPaths.kt](https://github.com/Sermilion/skill-bill/blob/b6fa1ae2a322b6b6addb1d018e867f92d5698f48/runtime-kotlin/runtime-infra/skills/src/main/kotlin/skillbill/infrastructure/skills/install/plan/CodexConfigPaths.kt) honors `CODEX_HOME`, otherwise chooses `.codex` if present or `.agents`. Installation target enumeration also includes discovered profiles and `.agents/skills`.
- [ClaudeConfigPaths.kt](https://github.com/Sermilion/skill-bill/blob/b6fa1ae2a322b6b6addb1d018e867f92d5698f48/runtime-kotlin/runtime-infra/skills/src/main/kotlin/skillbill/infrastructure/skills/install/plan/ClaudeConfigPaths.kt) honors `CLAUDE_CONFIG_DIR` and supports multiple roots.
- [InstallApplySkillLinks.kt](https://github.com/Sermilion/skill-bill/blob/b6fa1ae2a322b6b6addb1d018e867f92d5698f48/runtime-kotlin/runtime-infra/skills/src/main/kotlin/skillbill/infrastructure/skills/install/apply/InstallApplySkillLinks.kt) distinguishes existing links and occupied ordinary paths. Replacement eligibility uses the installed-skills root.
- [InstallSymlinkReplacement.kt](https://github.com/Sermilion/skill-bill/blob/b6fa1ae2a322b6b6addb1d018e867f92d5698f48/runtime-kotlin/runtime-infra/skills/src/main/kotlin/skillbill/infrastructure/skills/install/apply/InstallSymlinkReplacement.kt) stages temporary links and attempts restoration on failures.

These are behavioral references. A target being under an application directory is insufficient proof that a particular SkillLink attempt owns it. Profile enumeration and replacement behavior also need independent product decisions. We have not tested Skill Bill's implementation during this investigation.

## Agent compatibility evidence

These are documented locations, not a completed compatibility test matrix. `~` means the user's home directory on the agent's host.

| Agent | Documented global location | Findings and remaining evidence |
| --- | --- | --- |
| Claude Code | `~/.claude/skills/<name>/` | Directory symlinks are explicitly supported. Some names are reserved, including `synced`. The old `commands` directory is a legacy format. [Official documentation](https://code.claude.com/docs/en/skills) |
| Codex | `~/.agents/skills/<name>/` | Directory symlinks are explicitly supported. The current public table differs from Skill Bill's `.codex` preference. Verify profile and `CODEX_HOME` behavior before selecting destinations. [Official documentation](https://learn.chatgpt.com/docs/build-skills) |
| Junie CLI | `~/.junie/skills/<name>/` | Also reads `~/.agents/skills`; custom locations and disabling default discovery are configurable. IDE behavior and directory symlinks still need direct verification. [Official documentation](https://junie.jetbrains.com/docs/agent-skills.html) |
| Cursor | `~/.cursor/skills/<name>/` | Also reads `.agents`, Claude, and Codex skill directories. Directory symlink behavior and differences between CLI and editor still need direct verification. [Official documentation](https://cursor.com/docs/skills) |

### Agent selection does not imply exclusive visibility

The documented shared discovery paths mean that installing for Codex can expose a skill to Junie and Cursor. Cursor can also discover a skill installed in Claude's directory. This is an inference from their documented search locations; no live-agent test was performed.

We need to decide whether selection controls SkillLink-owned links or promises that only selected agents can load a skill. The latter needs more than filesystem links and may require agent configuration changes. Removing one link alone cannot justify a claim that an agent cannot load the skill.

Keep intended installation, observed link condition, and agent loading behavior distinct. A healthy link does not establish that an agent has enabled or loaded the skill.

### Skill format and identity

The [Agent Skills specification](https://agentskills.io/specification) requires `SKILL.md` with YAML frontmatter containing `name` and `description`. It constrains the name and requires it to match its directory. SkillLink currently permits preserved display spelling and requires case-insensitive uniqueness. We must decide how import treats format violations, directory and frontmatter disagreement, and agent-specific reserved names. Do not silently rewrite content to make it compatible.

The domain needs one pinned comparison rule. SQLite's built-in `NOCASE` only folds ASCII, so it cannot supply a general Unicode name policy. [SQLite collation documentation](https://www.sqlite.org/datatype3.html#collation)

## Filesystem and recovery constraints

Java NIO is a starting point, but the required guarantees need adapter-level evidence:

- Copying may leave partial output and follows source symlinks by default. File attribute preservation varies by filesystem.
- `ATOMIC_MOVE` ignores other options; behavior when a target already exists is implementation-specific. A preflight existence check followed by atomic move does not prove no replacement.
- Deleting a symlink removes the link, but checking ownership and then deleting by path still leaves a replacement race.

These behaviors come from the [JDK 21 Files contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html). `SecureDirectoryStream` supports operations relative to an open directory, but its availability depends on the provider and operating system. [JDK 21 SecureDirectoryStream contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/SecureDirectoryStream.html)

Windows exposes an unprivileged symlink creation flag that requires Developer Mode. The chosen JDK and filesystem must be tested with ordinary user permissions; an elevated CI run alone is insufficient. [Windows API documentation](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-createsymboliclinkw)

The existing architecture requires durable ownership records before mutation, one transactional catalog commit, rollback before commit, and completion of cleanup after commit. SQLite's transaction guarantees cover its database writes. They do not make the surrounding filesystem actions one transaction. [SQLite atomic commit](https://www.sqlite.org/atomiccommit.html)

Before implementing destructive import, settle:

1. How to identify and retain authority over the selected source while another process can edit or replace it.
2. How to publish the canonical directory and links without replacing a destination created after preflight.
3. How recovery identifies an artifact after a crash between filesystem mutation and recording completion.
4. How to preserve changed originals after commit and report cleanup pending.
5. How to handle source and library on different filesystems, permissions, executable bits, nested symlinks, hard links, and unsupported file types.
6. Whether crash guarantees cover process termination only or also power loss, and which durability steps follow from that scope.

There is also a product edge case. An explicitly selected source might already occupy a selected agent destination. Under the current collision rule, that import blocks. Supporting adoption in place needs an explicit ownership transition and a revised contract; it must not become an overwrite exception by accident.

## CLI and persistence options to validate

Skill Bill currently uses Clikt 5.1.0. Clikt provides subcommands, typed arguments, help generation, and command testing. It is a candidate for SkillLink's presentation boundary, with version compatibility still to check. [Clikt documentation](https://ajalt.github.io/clikt/)

Gradle's application plugin supplies launch scripts and distributions. Decide whether initial users supply Java or receive a bundled runtime. Also resolve how CLI launch tasks coexist with the existing Compose application tasks. No CLI launch command exists in SkillLink yet. [Gradle application plugin](https://docs.gradle.org/current/userguide/application_plugin.html)

The historical desktop database reference is commit `76cd64abcb8726002223917623e89c155d125797`, the parent of `211941b7a`. Its [database module](https://github.com/Sermilion/skill-bill/blob/76cd64abcb8726002223917623e89c155d125797/runtime-kotlin/runtime-desktop/core/database/build.gradle.kts) uses Room 3 and KSP. Its version catalog pins Kotlin `2.4.0-Beta2`, Room `3.0.0-alpha01`, and KSP `2.3.7`. SkillLink used Kotlin `2.0.21` when this investigation began. The subsequent toolchain upgrade does not establish Room and KSP compatibility; verify that combination separately.

Room documents JVM database construction and SQLite driver selection. Verify a compatible Room, KSP, and driver combination with transaction, migration, and resource-close tests before choosing versions. SQLite remains the accepted storage choice. [Room setup documentation](https://developer.android.com/kotlin/multiplatform/room)

## Proposed boundaries to develop in the spec

These refine the existing architecture without selecting new modules:

| Owner | Responsibility |
| --- | --- |
| Domain | Skill identity, comparison key, installation rules, typed state and outcomes |
| Application | Import and link operations, transaction boundaries, recovery decisions, cancellation policy |
| Infrastructure | Filesystem ownership mechanisms, agent destinations and capabilities, SQLite, diagnostics |
| CLI presentation | Parse requests, render outcomes, return exit status |
| App | Construct adapters and application services, own process lifetime and resource cleanup |

Adding an agent should add its location and compatibility behavior without changing the import protocol. Adding the desktop UI should reuse the application operations. Define ports around those real boundaries; defer plugin loading and general workflow machinery.

Persist exact selected destinations with installation ownership. Recomputing them from a later environment could unlink the wrong profile or strand an earlier link.

## Evidence needed before release

| Regression | Required evidence |
| --- | --- |
| Collision overwrites another skill | Real filesystem tests for ordinary files, directories, foreign links, dangling links, and a destination created after preflight |
| Import loses the only valid source | Fail copying and verification; replace or edit the source before cleanup; assert the original or replacement survives |
| Crash leaves an installed partial skill | Terminate a separate process around journal writes, publication, linking, commit, and cleanup; reopen and run recovery |
| Recovery removes a committed or unrelated skill | Preserve both while rolling back a failed attempt; repeat recovery after a cleanup failure |
| Two commands reserve the same name | Separate processes contend on writer ownership and database uniqueness |
| Name policy differs by host | Fixed non-ASCII comparison examples and platform-invalid names across Windows, Linux, and macOS |
| Link exists but the agent ignores it | Versioned live-agent discovery checks in isolated profiles, including shared-location visibility |
| Packaging hides permission or runtime problems | Run the packaged CLI under an ordinary user on each supported OS |

No installer, user skill mutation, live-agent discovery, or platform capability experiment ran during this research. Existing test sources were inspected, not executed. This document adds no claim of passing implementation tests.

## Next investigation

First resolve link selection versus exclusive visibility, supported agent clients and profiles, and the minimum accepted skill shape. Then specify the installation state transitions and run isolated filesystem and persistence experiments. Use their results to write the first implementation spec and its acceptance criteria.
