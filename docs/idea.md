# SkillLink product idea

Status: initial product direction with confirmed scope decisions. Usage tracking and the remaining open questions need further design work.

## The idea

Keep one copy of a skill and link it to every agent you use.

SkillLink is a local skill manager built around that promise. Users add a skill to a shared library, choose which agents should have access to it, and manage those installations through a simple interface. Symlinks connect agent skill locations to the shared source.

The library and its installation controls are the core product. Usage tracking can help users maintain the library as it grows, but SkillLink must remain useful when tracking is unavailable.

SkillLink starts as a command-line application for Windows, Linux, and macOS. A desktop interface is a later delivery stage. Skill installations are global for the current user. The app shows only skills it manages and installs.

## Where it came from

Skill Bill began with the idea of installing skills across agents through symlinks. It grew into a framework for skill authoring, orchestration, validation, and runtime workflows. SkillLink gives the original installation idea its own project and room to develop a focused user interface.

The early Skill Bill installer is a reference for this work. Commit `0ee27b14b`, which added Codex support, contains the small installer and agent-linking approach discussed during initial exploration. Use it to understand the installation model and lessons from agent integration. SkillLink's implementation should follow its own architecture and ownership, conflict, and recovery requirements. Copying historical code is not the objective.

SkillLink starts in a separate repository. Design its implementation around the product described here, with explicit boundaries between application policy, agent integration, filesystem operations, persistence, and presentation.

## What users should be able to do

- Add a skill once and make it available to selected agents.
- See the skills they manage and where each is installed.
- Read and edit the shared skill content.
- Find broken links and resolve installation conflicts.
- See recorded usage where an agent integration provides evidence.
- Identify skills worth disabling or removing without mistaking missing telemetry for inactivity.

The first release should answer two everyday questions: "Where is this skill installed?" and "Which copy should I edit?"

## Shared library and installation model

### One canonical source

Each managed skill has a canonical source in a SkillLink library. The library lives in SkillLink's own installation area, following Skill Bill's application-owned location model. It is independent of any particular agent. Removing an agent must leave the library intact.

Agent installations link to that source. Editing the shared content updates what those links point to. Users must restart affected agents to pick up changes. The interface should show a restart reminder after installation or content changes.

Follow Skill Bill's home-directory layout with SkillLink's own namespace: `~/.skilllink/` is the application-owned root, `~/.skilllink/skills/` holds canonical skills, and `~/.skilllink/skilllink.db` holds local data. Resolve `~` through the current user's home directory on Windows, Linux, and macOS. These are SkillLink paths; never reuse or modify `~/.skill-bill/`. Application binaries remain separate from this mutable user data. Later native installers place binaries according to platform packaging conventions. Application updates must preserve the managed library because imported originals no longer exist.

### Agent selection

Users choose which agents receive each skill. The interface shows installation state per agent and lets users enable or disable a skill there.

All installations use the agents' global skill locations for the current user. Project-specific installations are outside the scope.

The first release supports the same installation agents as current Skill Bill: Claude, Codex, Junie, and Cursor. Skill Bill's current installer declares these in `SUPPORTED_AGENTS`. Use its global agent-location resolution as the implementation reference, including supported configuration overrides. Resolving an agent destination does not authorize scanning unmanaged skills.

### Explicit import and ownership

SkillLink does not discover, list, or index unmanaged skills. A user explicitly selects a skill to bring under management. That selection authorizes moving ownership of the skill to SkillLink.

Import copies the selected skill into the managed library and verifies the copy. Keep the original recoverable until the installation commits, then remove it. A failed copy must leave the original intact. The completed import leaves one canonical source in SkillLink's managed location.

### Unique names and installation conflicts

Skill names must be unique under case-insensitive comparison on every supported operating system. For example, `Review` and `review` collide. Preserve the display spelling while using one consistent, locale-independent comparison rule for import, installation, and restore. If a name is already in use, installation blocks. SkillLink must not silently rename, merge, or overwrite a skill to make the installation proceed.

An occupied agent destination also blocks installation unless it is the existing managed link for that same skill. Checking the exact destination for a conflict does not add unmanaged skills to the catalog. Conflict checks happen before deleting the imported original.

A broken managed link should identify the affected skill and agent so the user can repair or remove it.

### Interrupted installations

An interrupted installation starts over for that skill after rollback. Do not resume a partially copied skill or leave partial links, staging files, or active catalog entries behind. Other skills remain untouched.

Rollback removes only artifacts owned by the failed attempt and preserves or restores the original source. Defer irreversible source removal until commit. If the app crashes, perform cleanup on the next launch before allowing a retry for that skill. A committed installation remains installed even if the app exits before showing completion.

If permissions or another filesystem error prevent cleanup, show the failure and block retry until cleanup completes. Do not claim there are no leftovers when cleanup failed. Durable recovery metadata may remain until it is no longer needed to complete rollback.

### Unlinking and deletion

Disabling a skill for an agent removes its managed link and preserves the source. Deleting the canonical source is a separate action with a different consequence.

Trash and undo are desired conveniences. Removing a managed skill should eventually move it to app-managed trash and remove its managed links, with undo restoring the skill and its prior installations. Restore must obey the same case-insensitive name and destination conflict rules.

Trash and undo are nice to have, rather than first-release blockers. Retention, permanent deletion, and the detailed restore flow remain to be designed. This is separate from removing an original folder after a committed import.

## Interface

### CLI first

The first release uses a CLI to import an explicitly selected local skill, list managed skills and their canonical paths, manage per-agent links, and report installation condition. Commands must report conflicts, blocked recovery, pending cleanup, and restart reminders. Unlinking preserves the canonical source.

The executable is `skill-link`. Installation takes a path to a skill file and an explicit selection of agents. The list shows numbered rows, but enable, disable, and remove take the skill name. Row numbers are display aids and cannot select a skill for mutation.

[SL-2](../.feature-specs/SL-2-cli-skill-installation/spec.md) specifies the command syntax, output, exit codes, distribution, and recovery requirements. Its proposed defaults import the selected `SKILL.md` with its containing bundle and move removed skills to local trash. Those source and removal choices remain design defaults for review. An integrated editor is deferred to the desktop stage; users can identify the canonical path to edit with their own tools.

### Later desktop interface

The main window uses two panels. Skill browsing stays visible while the user reads or edits a selected skill.

### Sidebar

The sidebar lists only skills managed by SkillLink, including managed skills currently disabled for all agents. It should support:

- Search by skill name.
- Filtering by agent.
- Selecting a skill to view its content.
- Indicators for broken links.
- Update indicators once source updates are supported.

Usage-based sorting and filtering may become useful when tracking exists. They are later design options rather than first-release requirements.

### Main panel

The main panel shows the selected skill's rendered content. It also provides its source location and controls for enabling it in each agent.

An edit view changes the shared source. The interface should make it clear that this is the copy linked to the selected agents.

Once tracking is available, a small activity section can show recorded uses, which agents used the skill, and when it was last used. Content remains the main focus of the panel.

### Shared installation flow

1. Explicitly select a local skill to bring under management.
2. Choose the agents that should receive it globally.
3. Check name uniqueness and destination conflicts. Block installation on a collision.
4. Copy the skill into the managed library and verify it while keeping the original recoverable.
5. Create the managed links, commit installation, remove the original, and show the resulting state. Roll back an interrupted attempt before starting over.
6. Remind the user to restart affected agents.
7. Identify the canonical path for editing. The later desktop interface provides a main panel for reading and editing.

If a destination conflicts with an existing installation, show the conflict before changing that destination.

## Usage tracking

Usage tracking should help users understand which skills they use and where. Candidate information includes:

- Number of recorded uses for each skill.
- Which agent recorded each use.
- Last recorded use.
- The observation period and agents covered by tracking.

### Evidence and limits

Symlinks alone do not reveal whether an agent used a skill. Tracking requires evidence from agent hooks, logs, or explicit instrumentation. The available evidence and collection method need investigation for each integration.

No tracking mechanism has been selected. Research should compare these options per agent, identify exactly what each can observe, and assess setup requirements and data access. Any tracking must stay limited to SkillLink-managed skills.

A skill being read does not prove that the agent followed its instructions. The definition of a recorded use must be explicit before the interface presents counts. If an integration only observes reads, the interface should describe those events as reads.

### Local activity storage

Store activity in a local SQLite database under SkillLink's application-owned root, following Skill Bill's local database approach. The historical desktop app used Room with SQLite; use that desktop persistence approach as the reference. Keep schema migrations and transaction ownership explicit.

Storage is decided; collection is still research work. Determine collection controls and retention alongside the agent integrations.

### Missing data

The interface must distinguish between:

- Recorded activity.
- No recorded activity during a known observation period.
- Tracking unavailable for an agent.

An untracked agent may still use a skill. Missing tracking must never silently become a zero-use claim.

For example, "No recorded use in 90 days across the two tracked agents" tells the user what the evidence covers. "Never used" would claim more than that evidence supports.

Tracking is optional. Importing, linking, browsing, and editing skills must work without it.

## Stale skills and cleanup suggestions

SkillLink can use recorded activity to suggest reviewing skills that appear inactive. A suggestion should explain the observation period and tracking coverage that produced it.

Inactivity alone is not enough to decide a skill has no value. An incident-response skill may be useful even if it is rarely needed. Users should be able to keep occasional skills out of cleanup suggestions.

The proposed cleanup flow is:

1. Identify a skill with no recorded use over a chosen period.
2. Show the evidence and any gaps in tracking coverage.
3. Let the user keep it, exclude it from future suggestions, or disable it for selected agents.
4. Keep source deletion separate from disabling.

The inactivity threshold and exclusion controls remain open. Suggestions should support a user decision; they should not automatically delete skills.

## Delivery stages

### First release: CLI, local library, and links

The first release should provide:

- A command-line application for Windows, Linux, and macOS.
- Explicit import of local skill folders, with verified copying and removal of the original after installation commits.
- A catalog limited to SkillLink-managed skills.
- A shared skill library in SkillLink's installation area, independent of agent installations.
- Global symlink management for Claude, Codex, Junie, and Cursor.
- Listing managed skills, their canonical paths, and their per-agent installation state.
- Detection of broken managed links and installation blocking on case-insensitive name collisions or destination conflicts.
- Rollback of interrupted skill installations before a fresh retry, without partial installation artifacts.
- Disabling an installation while preserving its source.
- Restart reminders after changes that affect agents.

The first release is useful when a user can manage one skill across multiple agents and always identify its shared source.

### Later: desktop interface

Add the two-panel browsing and editing interface using the same application operations and managed library. The existing desktop shell and native packaging remain groundwork for this stage. They are not requirements for shipping the first CLI release.

### Later: sources and updates

Add Git sources and update previews. Users should be able to inspect a proposed update before applying it to the shared copy.

Repository layout support, version selection, and handling of local edits still need design work.

### Nice to have: trash and undo

Add app-managed trash and undo for removed skills. Design retention and permanent deletion explicitly, and block restore on conflicts.

### Later: activity and cleanup

Add tracking integrations where reliable evidence is available. Show recorded usage by agent and last use, together with tracking coverage.

Use that evidence to support inactivity filters and cleanup suggestions. Tracking quality should determine which claims the interface can make.

## Product boundaries

Skill ownership and installation should guide scope decisions. Browsing, editing, source updates, and activity information support that job.

Workflow orchestration, governed feature execution, review pipelines, and runtime task management are outside the scope discussed for SkillLink. Reusing code from Skill Bill does not require bringing those systems with it.

### Technology direction

Use the existing Kotlin/JVM and Gradle foundation for the CLI. Retain Compose Multiplatform targeting desktop JVM and the Material 3 approach for the later desktop interface. Mobile and web are outside the scope.

For the later desktop release, use Compose Desktop native distribution packaging with DMG for macOS, MSI for Windows, and DEB and RPM for Linux. The historical desktop database module used Room and SQLite. Exact dependency versions should be selected during implementation rather than copied blindly from historical builds.

The historical reference is the parent of removal commit `211941b7a`. It contains `runtime-kotlin/runtime-desktop/build.gradle.kts`, the desktop core and feature modules, and the supporting Gradle convention plugins. Use these as stack and packaging references while keeping SkillLink's smaller product scope.

## Open questions

- Should SL-2's source-bundle and recoverable-removal defaults become the first-release contract?
- What evidence can each agent provide for usage tracking, and what counts as a use?
- Which collection controls and retention policy should local activity data use?
- How long should trash retain skills, and how should permanent deletion and conflicting restores work?

Implementation must also specify the locale-independent name comparison, durable installation commit and rollback protocol, and platform link handling. Their required behavior is already decided above.

These questions do not change the central promise: one shared skill source, linked to the agents the user chooses.
