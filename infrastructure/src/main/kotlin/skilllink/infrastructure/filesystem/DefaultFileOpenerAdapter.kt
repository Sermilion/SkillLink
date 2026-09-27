package skilllink.infrastructure.filesystem

import skilllink.application.ports.DefaultFileOpenOutcome
import skilllink.application.ports.DefaultFileOpenerPort
import java.awt.Desktop
import java.nio.file.Path

class DefaultFileOpenerAdapter : DefaultFileOpenerPort {
  override fun open(path: Path): DefaultFileOpenOutcome =
    try {
      if (!Desktop.isDesktopSupported()) {
        DefaultFileOpenOutcome.Unavailable
      } else {
        val desktop = Desktop.getDesktop()
        when {
          desktop.isSupported(Desktop.Action.EDIT) -> {
            desktop.edit(path.toFile())
            DefaultFileOpenOutcome.Opened
          }

          desktop.isSupported(Desktop.Action.OPEN) -> {
            desktop.open(path.toFile())
            DefaultFileOpenOutcome.Opened
          }

          else -> {
            DefaultFileOpenOutcome.Unavailable
          }
        }
      }
    } catch (_: UnsupportedOperationException) {
      DefaultFileOpenOutcome.Unavailable
    } catch (_: Exception) {
      DefaultFileOpenOutcome.Failed
    }
}
