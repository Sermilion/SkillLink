package skilllink.application.ports

import java.io.Closeable

sealed interface WriterLockOutcome {
  data class Acquired(
    val handle: Closeable,
  ) : WriterLockOutcome

  data object Busy : WriterLockOutcome

  data object IoFailure : WriterLockOutcome
}

interface WriterLockPort {
  fun tryAcquire(): WriterLockOutcome
}
