package skilllink.infrastructure.diagnostics

import skilllink.application.ports.DiagnosticsPort
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class FileDiagnostics(
  private val diagnosticsRoot: Path,
) : DiagnosticsPort {
  override fun record(
    eventCode: String,
    managedId: String?,
    detail: String,
  ) {
    writeLine("event=$eventCode id=${managedId ?: "-"} detail=$detail")
  }

  override fun reportSecondaryFailure(
    primaryCode: String,
    secondaryCode: String,
    detail: String,
  ) {
    writeLine("secondary primary=$primaryCode secondary=$secondaryCode detail=$detail")
  }

  private fun writeLine(line: String) {
    try {
      Files.createDirectories(diagnosticsRoot)
      val target = diagnosticsRoot.resolve("events.log")
      val payload = "${Instant.now()} $line\n"
      Files.writeString(
        target,
        payload,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
      )
    } catch (_: Exception) {
      System.err.println("diagnostic write failed: $line")
    }
  }
}
