# SL-2 subtask 1: Install and list skills

## Scope

Implement the parent spec's `skill-link install`, `list`, help, and version commands as a complete CLI path. Read the parent spec for exact source, name, agent, output, ownership, and recovery contracts. Implement the `cli` module, app wiring and distribution, domain values, application operations, filesystem and agent adapters, initial SQLite schema, writer ownership, diagnostics, and install recovery together.

Persist stable skill identity, remembered agent selections, enabled intent, and exact destinations from the start. Do not implement unused management operations or pre-create empty trash abstractions. The initial schema and recovery journal must be versioned so subtask 2 can extend them through an explicit migration.

List numbers are display-only. Import takes the selected `SKILL.md` and its supporting folder. Management commands arrive in subtask 2 and must not appear as functional commands yet.

Write `ownership-protocol.md` beside these specs to document the concrete platform primitives and evidence used before any original-source removal is enabled. Record dependency choices and actual platform validation in `implementation-notes.md`. Update the graph, enforcement inventory, README, and agent instructions to describe the implemented CLI boundary and current capabilities.

## Acceptance criteria

1. `cli` depends only on `application` and `domain`, `app` wires its CLI main, and build guard fixtures accept the intended graph and reject CLI access to infrastructure.
2. Launch configuration produces `skill-link` and `skill-link.bat`, while CLI help and version have tests asserting no desktop initialization or user-state access.
3. Install parsing requires a selected `SKILL.md` path and at least one supported agent, with tests for relative paths, spaces, duplicate agents, missing selections, and unknown input.
4. Domain and source-validation tests cover the restricted portable name grammar, uppercase lookup key normalization, non-ASCII rejection, Windows reserved names, malformed frontmatter, unsafe source roots, and unsupported filesystem entries.
5. Verified bundle copying, direct link publication, and active-catalog commitment have tests asserting byte and directory preservation, original-source safety, name and destination conflict rejection, and no unmanaged catalog entries.
6. SQLite adapters and versioned schema persist stable identity, unique name key, remembered selection, desired state, exact destinations, and operation evidence through adapter-owned transactions.
7. List code and tests show deterministic numbered rows, canonical paths, per-agent intent and observed condition, an empty uninitialized library, and explicit failures for inaccessible or invalid storage.
8. Agent adapters and capability fixtures cover the four selected agents, supported overrides, reserved destinations, direct symlinks, recorded path semantics, and unavailable Windows link capability.
9. Writer-lock and recovery tests use real filesystems and SQLite to assert rollback before commit, cleanup after commit, retry blocking, external replacement protection, concurrent writer rejection, and preservation of unrelated state.
10. Typed outcome rendering and diagnostic tests cover the parent's exit codes relevant to install and list, secondary failure preservation, restart reminders, and committed cleanup pending.
11. Documentation includes the concrete ownership protocol, source-bundle scope, shared agent visibility, actual launcher tasks, dependency choices, and the platform evidence required by the validation strategy.

## Non-goals

Enable, disable, remove, trash, restore, and the parent's general non-goals. Do not create success-returning placeholders for later commands.

## Dependency notes

Depends on merged SL-1 and the current toolchain foundation. No preceding SL-2 subtask. Use the existing build and packaging boundaries without requiring Skill Bill at runtime.

## Validation strategy

Run the parent validation strategy for install and list. Build and smoke-test the standalone CLI distribution. Test copying nested fixtures with executable scripts, empty directories, and dotfiles. Test source changes during copy and cleanup, case collisions, occupied and externally replaced destinations, and failure after partial link creation. Reopen real SQLite state and perform fresh-process recovery before retrying. Include process termination and writer contention evidence alongside fault-injected tests.

Run the relevant suite on Windows, Linux, and macOS and record unavailable platform configurations honestly. Document an unsupported ownership capability as a visible product limitation; do not silently switch to unsafe deletion or copied installations.

## Next path

Proceed to `spec_subtask_2_manage_skills.md` on the same feature branch after this subtask completes.
