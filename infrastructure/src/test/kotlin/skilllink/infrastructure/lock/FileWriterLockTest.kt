package skilllink.infrastructure.lock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.application.ports.WriterLockOutcome
import java.nio.file.Path

class FileWriterLockTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun rejectsConcurrentWriters() {
        val lockPath = temp.resolve(".writer.lock")
        val first = FileWriterLock(lockPath).tryAcquire()
        assertTrue(first is WriterLockOutcome.Acquired)
        val second = FileWriterLock(lockPath).tryAcquire()
        assertTrue(second is WriterLockOutcome.Busy)
        (first as WriterLockOutcome.Acquired).handle.close()
    }

    @Test
    fun rejectsAWriterHeldByAnotherProcess() {
        val lockPath = temp.resolve("process.lock")
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java")
        val process =
            ProcessBuilder(
                javaExecutable.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                WriterLockChild::class.java.name,
                lockPath.toString(),
            ).redirectErrorStream(true).start()
        try {
            assertEquals("READY", process.inputStream.bufferedReader().readLine())
            assertTrue(FileWriterLock(lockPath).tryAcquire() is WriterLockOutcome.Busy)
        } finally {
            process.outputStream.close()
            process.waitFor()
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}

object WriterLockChild {
    @JvmStatic
    fun main(args: Array<String>) {
        val lock = FileWriterLock(Path.of(args.single())).tryAcquire()
        if (lock is WriterLockOutcome.Acquired) {
            println("READY")
            System.out.flush()
            System.`in`.readBytes()
            lock.handle.close()
        }
    }
}
