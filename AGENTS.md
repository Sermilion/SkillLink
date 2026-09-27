# AGENTS.md

## Required reading

Read [docs/idea.md](docs/idea.md) before planning, designing, implementing, or reviewing any change. It owns the product scope, confirmed decisions, delivery stages, and open questions. Read [README.md](README.md) for the project overview.

Before designing, implementing, or reviewing code, also read:

- [ARCHITECTURE.md](docs/ARCHITECTURE.md) for hexagonal boundaries, the target module graph, ownership, persistence, installation recovery, and CLI and desktop lifecycles.
- [docs/code-principles.md](docs/code-principles.md) for Kotlin patterns, package and file limits, comments, imports, tests, and build conventions.
- [docs/observability-policy.md](docs/observability-policy.md) for failure reporting, diagnostics, recovery evidence, and activity-data boundaries.

These documents own their respective rules. This file is the entry point and product guardrail summary. Read any instructions and decision records in the affected area before editing it. Keep the documents consistent when decisions change.

## Project context

SkillLink manages one canonical copy of a skill and installs it into selected agents through symlinks. It starts as a CLI for Windows, Linux, and macOS, using the Kotlin/JVM and Gradle foundation used by Skill Bill.

The first release focuses on CLI import, managed-skill listing, enable, disable, remove, canonical paths, per-agent links, and installation status. The `skill-link` launch tasks cover install, list, enable, disable, remove, help, and version with name-based management semantics (never list row numbers). The later desktop UI has a sidebar of managed skills and a main panel for viewing and editing the selected skill. Use Compose Multiplatform for desktop JVM and the Material 3 approach from Skill Bill's removed desktop app. Package DMG, MSI, DEB, and RPM installers for that later desktop release. Use local SQLite storage, with the historical desktop Room integration as the persistence reference. Do not treat an implementation proposal as an accepted product decision.

Skill Bill's architecture principles inform this project. Its workflow engine, platform packs, skill-generation contracts, and orchestration machinery are not SkillLink requirements. This repository's instructions must work without a sibling Skill Bill checkout.

## Product contracts

- Show and track only skills managed by SkillLink. Do not discover, index, or display unmanaged skills.
- Import only a skill the user explicitly selects. Copy and verify it in the managed library. Keep the original recoverable until installation commits, then remove it. Never delete the only valid copy.
- Roll back an interrupted installation for that skill before retrying from the beginning. Clean up attempt-owned partial copies, links, and catalog entries. Recover after a crash on next launch; block retry if cleanup fails. Do not roll back other skills or an already committed installation.
- Follow Skill Bill's home-directory layout in a separate `~/.skilllink/` namespace, with canonical skills in `skills/` and local SQLite data in `skilllink.db`. Never share or mutate Skill Bill's state directory. Keep the library in SkillLink's application-owned installation area. Application updates and removal of an agent must preserve it.
- Support Claude, Codex, Junie, and Cursor in the first release, matching Skill Bill's current install agents. Install skills globally for the current user. Do not introduce project-specific installations.
- Skill names must be unique under consistent, locale-independent, case-insensitive comparison on every supported platform. Block installation on a name collision; do not silently rename, merge, or overwrite.
- Check exact agent destinations for conflicts before deleting the imported original. This check must not turn into discovery of unmanaged skills.
- Link agent installations to the canonical source. Unlinking preserves that source; deleting a managed skill is a separate operation. Trash and undo are desired conveniences; retention and permanent deletion still need design. Undo must obey name and destination conflict rules.
- Tell users to restart affected agents after installation or content changes.
- Keep usage tracking optional and limited to managed skills. Distinguish no recorded use from unavailable tracking. Do not present a file read as proof that an agent followed a skill.
- Base cleanup suggestions on stated observation periods and coverage. Do not automatically delete skills based on inactivity.

## Architecture requirements

Follow [the architecture](docs/ARCHITECTURE.md), including its design principles and dependency graph. The target modules are `domain`, `application`, `infrastructure`, `desktop`, `cli`, and `app`. The modules exist; `app` owns desktop and CLI composition plus packaging, `cli` owns terminal parsing and rendering, and `desktop` owns the initial library shell. Retain `app` as the sole composition root and keep command handling separate from business rules.

- Domain rules have no UI or IO dependencies. Application use cases own policy and ports; adapters implement those ports.
- `app` is the sole composition root. CLI and desktop code call application operations and never manipulate files, links, or database entities directly.
- Give each operation one state owner and each acquired resource one cleanup owner. Preserve cancellation and the primary failure.
- Keep filesystem mutation distinct from database commitment. Record ownership before mutations; roll back uncommitted attempts and finish cleanup for committed ones.
- Keep agent and operating-system behavior in adapters. Do not duplicate it across use cases or UI components.
- Use typed boundary failures, canonical schema and key owners, and explicit migration behavior. Do not silently turn failure into absence or success.
- Keep only abstractions justified by a current consumer, adapter boundary, or useful test substitute.

Do not introduce a new module or dependency direction without updating the architecture and explaining the concrete boundary it serves.

## Coding, tests, and quality checks

Apply [code principles](docs/code-principles.md). They own Kotlin conventions and numeric limits. Existing violations do not authorize new ones.

Before adding a test, name the realistic regression it catches. Exercise observable boundaries and use real temporary filesystems and SQLite where their semantics matter. Verify platform link behavior on Windows, Linux, and macOS. Use `bill-unit-test-value-check` when available; its absence does not remove the test-value requirements.

Run checks appropriate to the change. Document canonical build and verification commands when tasks exist. Do not claim a check ran when there is no corresponding task or tool.

## Enforcement status

The Gradle foundation enforces JVM conventions, quality checks, and direct production project-dependency rules. It does not scan source imports, comments, wire keys, or package and file limits. The architecture describes the target; unenforced rules still apply in review.

As guards are added, maintain one inventory pairing each mechanically enforced rule with its proving test. Keep review-only requirements separate. Never expand exemptions, suppressions, or baselines to accommodate a new violation.

## Writing and decision records

Write direct, active prose. Preserve names, numbers, and qualifications. Remove filler, praise, unsupported claims, and decorative language. Use sentence case headings and straight quotes. Avoid em dashes and canned conclusions.

Lead commits, PRs, and documentation with the concrete outcome and reason. State what changed and how it was verified. Keep comments and explanations out of product flows unless they help users make a decision.

Record lasting architecture choices in the owning area's `agent/decisions.md` when needed. Keep feature history focused on behavior and boundaries. Do not create empty records or copy Skill Bill's historical decisions as though they were made for SkillLink.
