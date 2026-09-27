# Observability policy

Status: requirements for implementation. No diagnostic pipeline exists yet.

## Purpose

Failures must be visible to the user and leave enough local evidence to diagnose incomplete installation or cleanup. Diagnostics and usage tracking are separate. A successful copy is not a skill-use event.

## Required records

Record operation start, commitment, completion, rollback, and blocked recovery with an operation identifier. Record every fallback, degradation, suppressed failure, and secondary cleanup failure. Normal absence is distinct from a failed read.

Use a structured record with event kind, timestamp, operation identity where available, affected managed skill identity, agent where relevant, typed failure code, and recovery status. Declare serialized keys and closed tokens under their owning contract.

Diagnostics supplement the durable installation journal; they are not the authority for recovery. The journal must commit before the filesystem actions whose ownership it records. Best-effort logging cannot replace that requirement.

## User feedback

Show the failed action, the affected managed skill or agent, and the available next step. A cleanup failure must remain visible and block a conflicting retry until resolved. Do not present an incomplete installation as complete.

Show restart reminders after installation or content changes. Do not claim agents have reloaded the skill merely because a link or file changed.

Do not show implementation details such as database table names or exception traces in the normal product flow. Make technical evidence available separately when it helps diagnose a failure.

## Privacy and scope

Keep diagnostics local. Do not log skill bodies, prompts, credentials, environment dumps, or the contents of agent conversations. Prefer managed identifiers to full source paths; redact user-specific paths in exportable diagnostics.

Inspect unmanaged source content only during a user-selected import, and do not add it to usage tracking or the catalog before management succeeds. Future activity collectors must filter to managed skills before persisting events.

A local SQLite event count must state what was observed and over what coverage period. Unavailable or failed tracking is not zero use.

## Failure of diagnostics

Diagnostic writes must be bounded. A logging failure must not replace the original operation failure or recursively trigger the same failed logger. Use an independent bounded fallback and show diagnostic unavailability when relevant.

Failure to write required recovery state blocks the destructive action. Failure to write optional diagnostics follows the reporting rule above. Do not treat these two failure classes as interchangeable.

Define retention and export behavior before adding diagnostic storage. Usage-data retention and trash retention remain separate product decisions.
