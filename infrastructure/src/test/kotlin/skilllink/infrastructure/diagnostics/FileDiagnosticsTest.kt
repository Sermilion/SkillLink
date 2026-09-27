package skilllink.infrastructure.diagnostics

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileDiagnosticsTest {
  @TempDir
  lateinit var temp: Path

  @Test
  fun keepsSecondaryFailureEvidenceSeparateFromPrimaryEvent() {
    val diagnostics = FileDiagnostics(temp.resolve("diagnostics"))

    diagnostics.record("install_failed", "skill-id", "primary")
    diagnostics.reportSecondaryFailure("install_failed", "rollback_blocked", "secondary")

    val log = Files.readString(temp.resolve("diagnostics/events.log"))
    assertTrue(log.contains("event=install_failed id=skill-id"))
    assertTrue(log.contains("secondary primary=install_failed secondary=rollback_blocked"))
  }
}
