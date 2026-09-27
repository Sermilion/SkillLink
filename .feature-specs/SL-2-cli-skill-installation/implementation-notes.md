# SL-2 implementation notes

## Dependencies

- SQLite access uses `org.xerial:sqlite-jdbc` (see `gradle/libs.versions.toml`). JDBC keeps persistence inside `infrastructure` without Room alpha alignment work.
- CLI parsing is implemented in the `cli` module without an additional parser dependency.

## Launcher tasks

- `./gradlew :app:runSkillLinkCli --args="--help"` runs the CLI main class on the module runtime classpath.
- `./gradlew :app:skillLinkCliDistribution` writes `app/build/skill-link-cli/bin/skill-link` and `skill-link.bat` plus `lib/` artifacts. JDK 21 is required to execute the distribution.

## Platform validation

| Configuration | Evidence |
| --- | --- |
| Linux symlink install path | `LinkCapabilityProbe` and filesystem adapter tests on Linux CI (pending validate phase) |
| Windows symlink unavailable | `LinkCapabilityProbe` failure path covered by adapter tests; full Windows CI not claimed here |
| macOS | Same probe and adapter code path; dedicated macOS CI not claimed in this subtask |

Shared agent visibility: Junie and Cursor documented discovery paths may load skills from roots SkillLink did not link. Install help and README state this limitation.

## Validation gaps

Full `./gradlew check`, packaged distribution smoke from outside the repository, and multi-process crash tests are owned by the validate phase. This note does not claim they have passed yet.
