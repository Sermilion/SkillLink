# SL-2 subtask 2: Manage installed skills by name

## Scope

Implement the parent spec's `skill-link enable <name>`, `disable <name>`, and `remove <name>` commands through existing application and adapter boundaries. Read the parent spec for exact defaults, per-agent behavior, exit codes, trash semantics, and recovery rules.

Name lookup uses the domain comparison key. Numbers in list output remain display-only. Add or re-enable explicitly selected agents, retain disabled selections, and remove a skill into app-managed trash. Extend the journal and SQLite schema with explicit migration fixtures. Reuse ownership capabilities from subtask 1 without creating a second mutation coordinator or duplicating agent resolution in commands.

Update `ownership-protocol.md` for reverse link transitions and trash moves. Extend `implementation-notes.md` with actual migration and platform evidence. Update help and README to document the completed command set, source preservation on disable, and retained trash content on remove.

## Acceptance criteria

1. Parser and application tests implement all three name-based commands, case-insensitive matching, default and explicit agent selections, not-found outcomes, and literal handling of numeric names without list-index lookup.
2. Enable and disable tests cover remembered selections, adding an agent, idempotent no-ops, all-agent defaults, missing links, foreign replacements, changed environment settings, and unchanged unselected agents.
3. Canonical-source validation prevents enabling missing or invalid content; disable can remove a proven owned dangling link without deleting canonical content.
4. Multi-agent operations and their regression tests commit desired state after link changes, roll back only owned changes before commit, and retain blocked recovery when a foreign replacement prevents safe compensation.
5. Remove code and real-filesystem tests retain content in a unique trash location, remove only owned links, exclude the skill from the active list, and preserve identity, name, and former agent selections in trash metadata.
6. Trash and recovery tests cover interruption around link removal, canonical-to-trash movement, and database commitment, including repeated recovery, rollback conflicts, same-name future imports, and unrelated skill preservation.
7. An explicit schema migration and real database fixtures preserve subtask 1 installations and journal records; incompatible or failed migrations retain the original state and block mutation.
8. CLI output and tests distinguish no-op, not-found, integrity failure, conflict, blocked recovery, completed removal, and committed cleanup pending, with restart reminders for changed links.
9. The tree contains no permanent-delete, auto-purge, implicit restore, or force-overwrite path, and documentation explains where removed content remains.
10. Updated ownership and contributor documentation describe the complete command set, its exact name-based semantics, and the validation evidence required below.

## Non-goals

Trash restore, permanent deletion, retention expiry, interactive prompts, rename, agent relocation, desktop controls, and the parent's general non-goals.

## Dependency notes

Requires subtask 1's usable CLI, stable identities, exact destinations, SQLite adapter, writer lock, and recovery protocol. Keep all work on the same feature branch. Do not rewrite the install path to add management policy to presentation code.

## Validation strategy

Run the parent validation strategy for the complete command set, plus migration from a populated subtask 1 database. Test mixed enabled and disabled selections, failed multi-agent enable, foreign replacement during disable, missing canonical content, trash destination conflict, and interrupted remove both before and after commit.

Use a packaged CLI in an isolated home for install, list, disable by name, enable a new agent, and remove. Remove an earlier list row and prove that the old displayed number cannot select the remaining skill. Verify disabled content remains byte-identical, trash retains the removed bundle, and another skill remains usable. Exercise process termination and reopen recovery on supported platforms. Record platform coverage and limitations rather than inferring macOS or Windows behavior from Linux.

## Next path

SL-2 is ready for its normal review and validation after this subtask. Desktop work and trash restore require separate specifications.
