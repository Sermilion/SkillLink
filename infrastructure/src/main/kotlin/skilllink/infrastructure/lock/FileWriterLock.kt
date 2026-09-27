package skilllink.infrastructure.lock

import skilllink.application.ports.WriterLockOutcome
import skilllink.application.ports.WriterLockPort
import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class FileWriterLock(
    private val lockPath: Path,
) : WriterLockPort {
    override fun tryAcquire(): WriterLockOutcome =
        runCatching {
            Files.createDirectories(lockPath.parent)
            val channel =
                FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            tryLock(channel)?.let { lock ->
                WriterLockOutcome.Acquired(
                    object : Closeable {
                        override fun close() {
                            lock.release()
                            channel.close()
                        }
                    },
                )
            } ?: WriterLockOutcome.Busy
        }.getOrElse { WriterLockOutcome.IoFailure }
}

private fun tryLock(channel: FileChannel) =
    try {
        channel.tryLock()
    } catch (_: OverlappingFileLockException) {
        channel.close()
        null
    }
