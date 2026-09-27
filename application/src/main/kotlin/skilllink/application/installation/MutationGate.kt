package skilllink.application.installation

import skilllink.application.ports.WriterLockOutcome
import skilllink.application.ports.WriterLockPort

class MutationGate(
    private val writerLock: WriterLockPort,
    internal val recovery: RecoveryCoordinator,
) {
    fun tryAcquire(): WriterLockOutcome = writerLock.tryAcquire()
}
