# Code principles

Read [the architecture](ARCHITECTURE.md) before changing code. This document owns Kotlin patterns, package limits, comments, and build conventions. The [enforcement inventory](PrincipleEnforcementInventory.md) identifies implemented checks; listed review-only requirements still apply during review.

## Type modeling

Use sealed hierarchies or enums for closed alternatives. Keep one closed outcome family together and use exhaustive `when` branches. Do not model incompatible states with independent nullable fields or flags.

Keep application requests and results in their owning area's model package. Prefer typed values to `Map<String, Any?>` at application boundaries. Introduce value types when they enforce an invariant or prevent a realistic mix-up, not for every primitive field.

Keep persistence entities, Compose state containers, and filesystem handles inside their adapters. Boundary values describe the operation and its outcome without exposing implementation objects.

## Failures and cancellation

Represent expected boundary failures as typed outcomes. Preserve distinctions such as a name collision, destination conflict, unavailable link capability, failed copy, and blocked cleanup. Presentation code chooses user-facing wording from these outcomes.

Validate untrusted input explicitly. Do not classify malformed input through `error()`, `require()`, bare exceptions, or blanket `runCatching`. Programmer invariants are different from user-input validation.

Propagate `CancellationException` before broad catches. Do not convert cancellation into success, missing data, or a generic failure. Bound required cleanup and keep enough durable evidence for later recovery if cleanup cannot finish.

Preserve the primary failure when cleanup fails too. Send secondary failures through the diagnostic port without replacing the original result. Follow the [observability policy](observability-policy.md).

## Dependency and interface discipline

Application services receive dependencies explicitly. One composition root wires them. Do not introduce service locators, mutable dependency singletons, or alternate dependency graphs in presentation code.

A port represents a needed adapter capability or test boundary. An interface with one implementation, one caller, and no boundary or useful substitute should usually be removed. Avoid wrappers that only forward and rename a call.

Prefer internal visibility for implementations and helpers. Expose only what other modules need. A public constructor is not a reason to make all its collaborators public.

## State and coroutine ownership

Expose immutable snapshots and named operations. Never hand helpers a mutable context containing every dependency. Pass the values or narrow capabilities they use.

Use structured concurrency with an explicit owner for every scope. Application-lifetime mutations and screen-lifetime observations have different owners. Keep blocking IO off the UI thread and propagate results through typed boundaries.

Avoid detached work, unbounded retries, and blocking bridges inside suspend functions. Tests should control clocks and execution contexts where time or scheduling affects behavior.

## Wire vocabulary and schema ownership

Governed serialized keys live once in an owning `*Keys` or `*PayloadKeys` object. Consumers reference that declaration in map construction and access. Closed enum tokens use the owning enum's `wireValue` and one validated decoder.

Keep keys local to their owner unless multiple production modules or a port contract need them. Do not copy string values into test tables or another module. Tests should compare behavior against the authoritative contract.

Version persisted contracts. Document migration and rejection behavior. Do not introduce a schema framework for a value that never crosses a durable or external boundary.

## Package clustering and file limits

Use product areas and noun families within the module structure in the architecture document. Keep related inputs and results in area-owned model packages. Avoid generic cross-area `service`, `model`, and `utils` buckets.

Production non-model packages may contain at most 12 sibling Kotlin files. Area-owned model packages may contain at most 20. A production Kotlin file may contain at most 1,200 lines; test files are outside that line ceiling.

These ceilings are upper bounds. Split by responsibility and preserve related behavior. Do not hide dependencies in context objects or create arbitrary numbered files to satisfy limits.

If mechanical enforcement is added, its inventory owns the numeric constants and verifies these documented limits. Do not maintain conflicting test-local copies.

## Imports and names

Use imported simple type names in production and test Kotlin. Use an import alias when names collide. Inline fully qualified type references are not an alternative style.

Package declarations, imports, serialized strings, generated code, and necessary compiler disambiguation are separate cases for the guard to handle explicitly. Do not add broad exemptions.

Choose names that describe the behavior or domain concept. Preserve established contract names. Do not cycle synonyms for the same concept across an area.

## Comments

Authored Kotlin, including tests and build logic, must contain no `//` comments or non-KDoc block comments. KDoc is allowed only on interfaces and their members, including nested types inside an interface.

Use clear names and small functions. Put necessary architectural rationale in the owning area's `agent/decisions.md`. Test fixtures for comment scanners should construct forbidden syntax as data rather than introduce forbidden comments into test source.

## Gradle and generated files

Share JVM toolchain, compiler, and test configuration through convention plugins once multiple modules need it. Module builds declare their own capabilities and dependencies. Do not infer capability from whether a directory happens to exist.

Test convention behavior through Gradle task outcomes or plugin application. Do not prove a build rule by reading and matching the plugin source text.

Do not commit generated database implementation code, build output, installed libraries, or application state. Version Room migration schema exports when they are the authoritative migration-test inputs; they are distinct from generated runtime code.

## Test value

Name the realistic bug before writing a test. Favor observable boundaries and failure paths. A failed import preserving the original is valuable evidence; a mock verifying that a forwarding method was called is usually not.

Use actual temporary filesystems and real SQLite databases for ownership, migrations, and transaction behavior. Substitute ports when testing application decisions independently. Test doubles must preserve the port semantics relevant to the test.

Do not duplicate production algorithms in assertions. Avoid fragile timing, incidental prose, exact internal call counts, or reflection over implementation structure unless the structure itself is the enforced architecture contract.

Use `bill-unit-test-value-check` when available for test-quality review. The requirements above apply whether or not that tool is installed.

## Enforcement and exceptions

Each automated rule must name the test that proves it in one enforcement inventory. Include acceptance and rejection cases. Document what remains review-only and why it resists a reliable mechanical check.

Fix new violations. Do not suppress checks or expand exemptions to make a change pass. Existing debt is not permission to add more. Update architecture documents when an intentional boundary change is made, with a concrete reason and its consequences.
