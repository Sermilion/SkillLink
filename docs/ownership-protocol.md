# Ownership protocol (SL-2)

## Scope

SkillLink coordinates filesystem mutations through a process-owned writer lock, durable operation journal, and ownership checks before destructive steps. SQLite owns the active catalog, remembered agent selections, desired enabled state, exact destinations, trash metadata, and journal records. Filesystem inspection owns observed link condition.

## CLI command contract

The CLI command set is `install <SKILL.md> --agent <agent>...`, `list`, `enable <name> [--agent <agent>...]`, `disable <name> [--agent <agent>...]`, `remove <name>`, `help`, and `version`. Management commands use the case-insensitive domain name key; list numbers are display-only and a numeric name is treated literally. Enable defaults to remembered agents and explicit agents add or re-enable selections. Disable defaults to all remembered agents and explicit agents limit the change. Remove rejects agent options and moves the complete bundle to trash.

## Install (subtask 1)

See [.feature-specs/SL-2-cli-skill-installation/ownership-protocol.md](../.feature-specs/done/SL-2-cli-skill-installation/ownership-protocol.md) for source removal and initial link creation primitives.

## Enable and disable

- Enable validates canonical bundle content, uses recorded destinations for remembered agents, and resolves new destinations only for explicitly added agents.
- Link creation uses direct directory symlinks with no-replace semantics. Foreign or unreadable destinations block the operation.
- Disable removes only proven owned symlinks pointing at the canonical bundle. Canonical content stays byte-identical under `~/.skilllink/skills/`.
- Desired state commits in SQLite only after selected links reach the requested condition. Interrupted operations roll back attempt-owned link changes using the journal snapshot when ownership can be proven.

## Remove

- Remove deletes only owned agent links, moves canonical content to `~/.skilllink/trash/<operation-id>/` without overwrite, and commits catalog removal plus trash metadata in one transaction.
- Trash records retain skill identity, original name, former canonical path, trash location, and former agent selections. Trash names do not reserve active catalog names.
- Missing or invalid canonical content is an integrity failure; links are not removed and removal does not complete.
- SL-2 does not implement trash restore, permanent deletion, auto-purge, or force overwrite.

## Recovery limits

Writer locking uses `FileChannel.tryLock` on `~/.skilllink/.writer.lock` and coordinates SkillLink CLI instances only. Crash tests target process termination; power-loss durability is not claimed.

## Validation evidence

Validation must include parser and renderer assertions for every typed outcome, real temporary-filesystem tests for owned links, foreign replacements, dangling links, environment changes, trash byte preservation, and unrelated-skill preservation, and real SQLite fixtures for versioned migration, journal retention, failed migration, and blocked mutation. Supported environments must also exercise process interruption, repeated recovery, rollback conflicts, same-name future imports, and concurrent writer contention. Platform results must name the host and state unavailable Windows or macOS configurations instead of inferring coverage.
