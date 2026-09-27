package skilllink.application.ports

import java.nio.file.Path

sealed interface DefaultFileOpenOutcome {
  data object Opened : DefaultFileOpenOutcome

  data object Unavailable : DefaultFileOpenOutcome

  data object Failed : DefaultFileOpenOutcome
}

interface DefaultFileOpenerPort {
  fun open(path: Path): DefaultFileOpenOutcome
}
