## [2026-09-27] SL-2 manage installed skills by name
Areas: application/installation, application/ports, app, cli, infrastructure/filesystem, infrastructure/layout, infrastructure/persistence, docs, tests
- Added name-based enable, disable, and remove flows with remembered and default agent selection, case-insensitive lookup, no-op and conflict outcomes, and restart reminders.
- Extended ownership and recovery coordination for reverse link transitions and trash moves; SQLite catalog and journal state preserves disabled selections and removed-bundle metadata.
- Reused the existing mutation, writer-lock, filesystem-ownership, and application-composition boundaries; CLI code remains presentation-only.
- Removed bundles move to SkillLink-managed trash; restore, permanent deletion, retention expiry, and force-overwrite remain out of scope.
Feature flag: N/A
Acceptance criteria: 10/10 implemented for subtask 2
