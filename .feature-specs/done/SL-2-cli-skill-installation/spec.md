# SL-2: Install and manage skills through the CLI

Status: prepared specification. Implementation has not started.
Mode: `decomposed`, with two executable subtasks.

## Outcome

Provide a `skill-link` executable that imports a skill selected by its `SKILL.md` path, links it to explicitly selected agents, lists managed skills with display numbers, and manages them by skill name. Support Claude, Codex, Junie, and Cursor globally for the current user on Windows, Linux, and macOS.

Use Skill Bill's symlink installation principle. Implement SkillLink's own application operations, ownership rules, persistence, and recovery. No runtime dependency on Skill Bill or its checkout is allowed.

## Required reading

- `../../../AGENTS.md`
- `../../../docs/idea.md`
- `../../../docs/ARCHITECTURE.md`
- `../../../docs/code-principles.md`
- `../../../docs/observability-policy.md`
- `../../../docs/cli-installation-research.md`

## Scope and design defaults

The user confirmed the executable name, file-path input, numbered list, management by skill name, and agent selection. This specification uses two design defaults for the remaining choices: the file selects its containing skill bundle, and removal moves managed content into local trash. These defaults are explicit here so they can be reviewed before implementation.

Import includes the selected `SKILL.md` and supporting files in that one skill folder. It does not import a repository, discover adjacent skills, or accept an arbitrary Markdown document. Remove retains recoverable content; this feature has no permanent deletion or automatic trash expiry.

## Command contract

```sh
skill-link install /path/to/code-review/SKILL.md --agent claude --agent codex
skill-link list
skill-link disable code-review
skill-link disable code-review --agent claude
skill-link enable code-review
skill-link enable code-review --agent cursor
skill-link remove code-review
skill-link --help
skill-link --version
```

Use the same syntax in the Windows launcher. Paths containing spaces work with ordinary shell quoting. Relative paths resolve against the invocation's working directory. The executable is `skill-link`; the existing project name and `~/.skilllink/` data namespace do not change.

| Command | Required behavior |
| --- | --- |
| `install <skill-file> --agent <agent>...` | Requires at least one explicit agent. Validates the entire request before mutations, imports one bundle, and enables it for every selected agent as one operation. No implicit all-agents default. |
| `list` | Lists every active managed skill, including skills disabled everywhere. Shows a display number, exact name, canonical path, and per-agent desired state and observed link condition. |
| `disable <name> [--agent <agent>...]` | Without options, disables every configured installation of that skill. With options, disables only those agents. Preserves content and remembered agent choices. |
| `enable <name> [--agent <agent>...]` | Without options, enables the skill for its remembered agents. Explicit options can add agents or re-enable a subset. Requires an intact canonical source and rechecks destinations. |
| `remove <name>` | Removes the skill from the active catalog, removes its owned agent links, and moves canonical content to app-managed trash in one recoverable operation. Rejects `--agent`, because partial unlinking is `disable`. |

Agent identifiers are `claude`, `codex`, `junie`, and `cursor`. Reject unknown agents and options before acquiring mutation ownership. Repeated agent options deduplicate. Commands do not prompt, require a TTY, expand globs internally, or interpret skill content as instructions.

Management arguments are skill names, never row numbers, positions, paths, or internal IDs. Match names case-insensitively through the domain comparison key. `disable 3` searches for a skill literally named `3`; it must never resolve list row 3. Names from older list output must not retarget another skill because the list changed.

List sorts by the domain name key and numbers rows starting at 1 for each invocation. The number is presentation only and is not persisted. Empty catalog output states that no managed skills exist. Trashed skills are excluded. Render names, paths, and errors as plain text with terminal control characters escaped. Do not scan directories to assemble the catalog.

Persist a stable internal skill ID independent of names and display order. Remember agent selection separately from enabled intent. An already disabled selected installation is a successful no-op. Disabling an agent never selected for that skill is also a no-op and does not add it. An enabled installation with its correct owned link is a successful no-op. Enabling an intended installation whose link is missing recreates the link after conflict checks. A foreign replacement remains a conflict.

A mutation affecting multiple agents succeeds for all selected agents or rolls back its uncommitted changes. A rollback conflict reports blocked recovery; it must not masquerade as atomic success. Other agents and skills remain unchanged.

## Output and exit status

Successful mutations report the exact skill name, canonical or trash path as appropriate, affected agents, and any remaining cleanup. Show a restart reminder for agents whose links changed. Do not claim that an agent loaded the skill. No-op commands say that no changes were needed.

Use stdout for help, version, list data, and completed outcomes. Use stderr for failures and incomplete outcomes. Help and version must work without opening or creating the library. Do not initialize Compose on any CLI path. A missing library produces an empty list without creating it. An inaccessible or invalid database is an error, not an empty library.

| Exit code | Meaning |
| --- | --- |
| `0` | Completed operation, successful no-op, list, help, or version. |
| `2` | Invalid arguments, unsupported skill format, or invalid skill name. |
| `3` | Requested managed skill does not exist. |
| `4` | Name or destination conflict, another writer, or blocked recovery. |
| `5` | IO, persistence, migration, or unavailable platform capability failure. |
| `6` | Operation committed but required cleanup is pending. Output explicitly identifies it as committed. |
| `130` | Cooperative cancellation before commitment. Any unfinished rollback remains recorded for recovery. |

If the process terminates abruptly, the shell controls the observed exit status; recovery uses durable state. Preserve the primary failure and report secondary cleanup or diagnostic failures separately. Normal output omits traces and database internals. JSON output, completion scripts, and interactive menus are outside SL-2.

## Skill input and name contract

Accept an existing regular file named exactly `SKILL.md`. Its containing directory is the bundle root and must match the frontmatter `name`. Read UTF-8 Markdown with YAML frontmatter, requiring scalar `name` and nonempty scalar `description`. Reject duplicate required keys, unsafe YAML tags or object construction, malformed text, and path-invalid names. Preserve the original file bytes and other frontmatter fields; do not reserialize user content.

For SL-2, import names follow the Agent Skills portable grammar: 1 to 64 ASCII lowercase letters, digits, and hyphens, with no leading, trailing, or consecutive hyphens. Reject Windows device names case-insensitively. This restricted grammar makes the comparison key ASCII lowercase on every platform. Management lookup permits uppercase ASCII and applies the same key. Non-ASCII names are rejected explicitly, including composed and decomposed accented names. Do not defer comparison to SQLite `NOCASE`, the host locale, or filesystem collation. Expanding the accepted grammar requires a versioned name-policy decision and migration analysis.

Import supporting regular files and directories, including dotfiles. Preserve file bytes, relative structure, empty directories, and executable intent where the platform supports it. Do not apply repository ignore rules. Reject symbolic links, junctions, reparse points, special files, and unsupported entry types throughout the selected tree. Do not follow them. Reject a bundle rooted at a filesystem root, the user's home, an agent skills root, inside SkillLink's data root, or a directory containing `.git`. Reject source and destination overlap, including aliases resolved by the platform adapter.

The selected file authorizes import of that bounded folder only. Never traverse siblings or a repository to find other skills. Source changes during verification must fail safely or leave committed cleanup pending as appropriate. A pre-existing installation at the requested agent destination is a conflict even if it is the selected source; automatic adoption of existing agent installations is outside SL-2.

## Agent destinations and visibility

Implement one registry of supported agent adapters. Adding an agent must not add branches throughout the application operations or CLI parser. Each adapter resolves destinations and declares capabilities; it does not own installation policy.

The initial default destinations are:

| Agent | User skill root |
| --- | --- |
| Claude | `~/.claude/skills`, or `skills` under an explicitly set `CLAUDE_CONFIG_DIR` |
| Codex | `~/.agents/skills` |
| Junie | `~/.junie/skills` |
| Cursor | `~/.cursor/skills` |

These defaults come from the official documentation recorded in `../../../docs/cli-installation-research.md`. Verify them against official documentation during implementation and record the supported configuration matrix. Do not inherit historical `.codex/skills` or profile fan-out behavior from Skill Bill without current agent support. SL-2 does not discover profiles, read project configuration, or edit agent configuration. A malformed supported override is an explicit failure. Claude's reserved destination names from the research are conflicts for that adapter.

Agent selection controls the links SkillLink creates. It does not promise exclusive visibility: Junie, Cursor, and other agents may also read shared skill roots. State this limitation in install help and README. Do not silently change other agents' discovery settings.

Persist the exact resolved destination for each managed installation. Disable and remove operate on recorded destinations even after environment settings change. Enabling an existing selection uses its recorded destination; adding an agent resolves a new destination. Relocation is later work. If selected adapters resolve the same exact destination, store one owned link with explicit references; removing one reference cannot unlink a path still required by another enabled reference. Do not infer ownership just because an unmanaged link points to the expected source.

Use direct directory symlinks to the canonical bundle. Do not use another agent's installation as an intermediate target. Missing agent applications do not prevent creation of their global skill roots. An unavailable symlink capability produces a typed error with platform-specific next steps, with no copy or junction fallback.

## Architecture and distribution

Add a `cli` presentation module with dependencies only on `application` and `domain`. It owns parsing, rendering, and exit-code mapping. This concrete boundary prevents command code from importing filesystem or SQLite adapters and lets the later desktop consume the same operations. `app` remains the sole composition root and gains a dependency on `cli`. Other module edges remain as documented.

Domain owns validated names, stable identity, agent identity, comparison keys, and state rules. Application owns install, list, enable, disable, remove, and recovery operations with typed requests and outcomes. Infrastructure owns source inspection, verified copying, native filesystem capabilities, agent resolution, SQLite, writer locking, and diagnostics. Keep command parsing and OS-specific branches out of application policy.

Update the dependency guard and its proving fixtures for the new module and allowed edge. Add a rejection fixture for `cli -> infrastructure`. Update architecture, AGENTS, README, and the enforcement inventory to describe what exists after implementation. Do not add a generic workflow engine, plugin loader, ports module, or framework copied from Skill Bill.

Keep the existing desktop entry point. Add a separate CLI main and Gradle run task under `app`, plus a CLI distribution with `bin/skill-link` and `bin/skill-link.bat`. The initial distribution requires JDK 21 and includes its JVM dependencies. It must run outside the repository without Gradle or Skill Bill. Native installers and bundling a CLI runtime are later work. Document actual task names and launch commands when implemented; do not advertise nonexistent tasks as runnable today.

Select compatible stable parser, SQLite, YAML, and any native filesystem dependencies during implementation using official compatibility documentation. Pin versions in the catalog and record the reason for each. Use the historical Room approach as a persistence reference, but do not adopt an alpha dependency solely to reproduce it. Any alternative SQLite adapter must preserve the application boundary, schema exports or equivalent versioned schema, and migration tests.

## Ownership, commitment, and recovery

SQLite owns the active catalog, remembered selections, desired enabled state, exact destinations, trash metadata, and versioned operation journal. Managed files own content. Filesystem inspection owns observed link condition. Store a unique domain name key for active skills. Names in trash do not reserve active names. A future restore must recheck names and destinations.

Take a process-owned exclusive writer lock for the SkillLink root before recovery or mutations. Do not break it using a PID file or timeout guess. A second live writer receives a typed busy result. List reads a consistent catalog snapshot and inspects only recorded paths; it reports ongoing or pending operations and does not run repairs. All mutations first recover interrupted operations; unresolved recovery blocks further mutations in SL-2. Recovery handles each recorded operation without mutating unrelated skills.

Persist operation identity, kind, affected skill, previous and intended state, exact paths, ownership evidence, and recovery phase before each associated mutation. Durable intent alone does not prove that a later artifact belongs to that operation. Filesystem creation, publication, and rollback must establish and check ownership under replacement races. Use no-replace operations, owned staging, and platform identity evidence. A preflight existence check followed by overwriting move, or hash verification followed by unprotected recursive deletion, is not sufficient.

Install proceeds through validation and reservations, verified staging copy, canonical publication, agent-link creation, and one SQLite transaction committing catalog state and the journal's committed marker. Keep the original recoverable throughout. Before commit, cancellation or interruption rolls back only attempt-owned artifacts and releases the reservation after cleanup. Recovery starts a future retry from a fresh copy. After commit, recovery finishes source cleanup and never rolls back the installed skill.

Design and document the actual Linux, macOS, and Windows source-removal and link-unlink protocols in `ownership-protocol.md` beside this spec before enabling destructive operations. Cover externally replaced paths, edited files, directory renames, and crashes between filesystem operations and journal updates. If the platform cannot establish the ownership required for a destructive step, retain the data and report blocked or pending cleanup. Do not weaken the safety contract to make an unsupported primitive appear portable.

Enable and disable use the same journal and writer ownership. Commit desired state only after the selected links reach the requested condition. Before commit, rollback restores the previous state where owned paths permit it. External replacements prevent destructive rollback and remain visible as blocked recovery. Missing links are an observed condition; foreign files, foreign symlinks, and unreadable paths are separate typed outcomes.

Remove preflights every recorded installation, removes only owned links, and moves canonical content to `~/.skilllink/trash/<operation-id>/` with no overwrite. Persist enough evidence to restore the canonical location and prior links before commit. Commit active-catalog removal and the trash record together. After commit, recovery finishes only cleanup. Retain skill identity, original name, content location, and prior agent selection in trash metadata. Print the retained path. SL-2 never purges trash, never implements restore implicitly, and never follows links while removing artifacts.

A missing canonical source blocks enable and remove with an explicit integrity failure. Disable may still unlink proven owned dangling links. An absent managed link can be treated as already unlinked; a foreign replacement blocks the operation. Removing an unknown or already removed name returns not-found. No `--force` bypass is included.

Version the initial database schema and operation payloads. Reject incompatible future versions and failed migrations without recreating or erasing data. Keep database transactions narrow and adapter-owned; filesystem operations are not part of a SQLite transaction. Require process-crash recovery. Document flush guarantees and filesystem limitations separately; tests of process termination are not proof of power-loss durability.

## Diagnostics

Record operation start, commitment, completion, rollback, and blocked recovery through the diagnostic port. Keep recovery evidence separate and mandatory. Store bounded local diagnostic files under `~/.skilllink/diagnostics/`, with at most three files of 1 MiB each. Rotate only diagnostic files. No diagnostic export command is included, and logs contain managed IDs and failure codes rather than skill contents, credentials, environment dumps, or full user paths. A failed optional diagnostic write uses a bounded stderr fallback and cannot replace the primary outcome.

## Acceptance criteria

1. The tree contains a separate CLI presentation module and app-owned CLI entry point, launcher/distribution configuration for `skill-link`, documented JDK requirements, and guard fixtures covering the updated dependency graph.
2. Parser and command tests cover explicit agent selection, file paths with spaces, invalid agents, management by case-insensitive name, and rejection of any row-number interpretation.
3. Import code and real-filesystem regression tests cover complete bundle copying, byte preservation, source validation, case-insensitive collisions, occupied destinations, unsupported links inside sources, and preservation of the original on failed import.
4. Application operations and SQLite schema distinguish stable identity, name key, remembered agents, desired enabled state, exact owned destinations, observed condition, and trash records without cataloging unmanaged skills.
5. List rendering and application tests cover deterministic display numbering, disabled skills, empty catalog, canonical paths, broken links, foreign replacements, unreadable paths, and incomplete operations.
6. Enable, disable, and remove implementations and regression tests follow the command table, preserve unrelated agents and skills, and retain removed content in trash without permanent deletion.
7. The durable journal, writer lock, and recovery implementation have real-filesystem and SQLite tests covering failures before and after commit, external path replacement, interrupted rollback, repeated recovery, and concurrent processes.
8. Agent adapters, capability tests, and documented compatibility evidence cover Claude, Codex, Junie, and Cursor, direct links, recorded destinations, shared visibility, and Windows symlink failures.
9. Typed result mappings, output tests, and diagnostic handling distinguish conflict, not-found, blocked recovery, unavailable capability, failure, cancellation, and committed cleanup pending using the specified exit codes.
10. Versioned persistence contracts, migration fixtures, ownership-protocol documentation, and contributor documentation describe recovery and unsupported capabilities without claiming validation that was not performed.

## Constraints and non-goals

No desktop features, editor, activity tracking, discovery of unmanaged skills, Git imports, source updates, project installs, profile discovery, agent configuration rewriting, rename command, permanent deletion, trash restore, auto-purge, or release publishing. No automatic adoption or replacement of occupied agent destinations. No source implementation is required by this spec-preparation task.

All tests isolate application and agent roots in temporary directories. They never modify the developer's actual skills or state. Maintain the existing quality gates and do not add exemptions to fit the implementation.

## Validation strategy

Run the repository's `./gradlew check` and `./gradlew build` with JDK 21 after each executable subtask. Run the new CLI distribution and help smoke tests through the tasks documented by that subtask. Use the corresponding Windows wrapper commands where applicable.

Exercise application outcomes with narrow port substitutes and test persistence and filesystem semantics with real temporary directories and SQLite. Inject failure at every mutation and journal transition, then reopen state to verify rollback before commit and cleanup after commit. Kill a subprocess at representative durable boundaries so exception-based tests are not the only crash evidence. Use two CLI processes to test writer contention.

Run the filesystem and launcher suite on Linux, Windows, and macOS. Windows coverage includes symlink creation when permitted and the typed capability failure when unavailable. Test actual replacements between inspection and mutation, case-colliding destinations, same-target foreign symlinks, source edits during copying, full or inaccessible storage, and failed cleanup. Verify supported source permissions and executable files. Record the platforms and configurations actually exercised in implementation notes.

Check that a packaged invocation from outside the repository can install, list, disable, enable, and remove a fixture using isolated home and agent roots, without a window or a sibling checkout. Check that ordinary help does not initialize storage. Review test value against the concrete data-loss, ownership, and stale-selection regressions named above.

## Delivery and dependency notes

1. [Install and list](spec_subtask_1_install_and_list.md) delivers a usable CLI for importing into selected agents and inspecting its managed library, including persistence and recovery.
2. [Manage installed skills](spec_subtask_2_manage_skills.md) adds name-based enable, disable, and recoverable removal through the same boundaries.

The first subtask is independently usable. The second adds a separately shippable management workflow and expands recovery to reversing link changes and trash moves. Each subtask includes its own tests and documentation; neither is divided by architecture layer.

## Next path

Start the prepared goal with `skill-bill goal SL-2`. Spec preparation does not start implementation.
