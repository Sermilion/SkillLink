# Ownership protocol (SL-2 install and list)

## Scope

This document records the concrete primitives used before original-source removal and owned link cleanup in subtask 1. Destructive removal of imported sources runs only after database commitment and identity checks described here.

## Linux and macOS

- Canonical publication uses `Files.move` into `~/.skilllink/skills/<name-key>` with prior existence checks (no replace).
- Agent links use `Files.createSymbolicLink` from the recorded destination to the canonical directory.
- Rollback deletes attempt-owned paths with `Files.deleteIfExists` after verifying symbolic links point at the expected canonical target when applicable.
- Source removal after commit compares the recorded tree fingerprint, including relative entries, content, executable intent, and filesystem identity, then renames the unchanged source into a same-filesystem quarantine before deletion. A changed or replaced source remains and is reported as cleanup pending.

## Windows

- The same no-replace publication and direct directory symlink creation apply when `LinkCapabilityProbe` succeeds.
- When symlink creation is unavailable (typical without Developer Mode or equivalent), install fails with a typed capability outcome. SkillLink does not copy skills into agent directories as a fallback.

## Evidence limits

Process-owned writer locking uses `FileChannel.tryLock` on `~/.skilllink/.writer.lock`. This coordinates SkillLink CLI instances only; it does not exclude external editors or agents from mutating paths.

Crash guarantees in tests target process termination and lock contention. Power-loss durability is not claimed.

## Enable, disable, and remove

- Enable recreates owned symlinks after conflict checks; disable unlinks owned symlinks only.
- Remove moves canonical directories into `~/.skilllink/trash/<operation-id>/` with `Files.move` and no overwrite.
- Management rollback restores link state from journal snapshots; foreign replacements block destructive rollback.

## Safe removal prerequisites

Before deleting an imported bundle after commit:

1. Database transaction recording the skill and installations has committed.
2. Canonical content and owned links exist at recorded paths.
3. Source root identity and content match the fingerprint captured before staging copy.
4. If identity cannot be established, the original tree is retained and cleanup pending is recorded.
