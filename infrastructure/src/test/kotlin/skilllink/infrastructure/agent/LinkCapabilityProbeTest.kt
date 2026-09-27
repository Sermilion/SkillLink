package skilllink.infrastructure.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import skilllink.application.ports.LinkCapability
import java.nio.file.Path

class LinkCapabilityProbeTest {
    @Test
    fun reportsUnavailableWhenPlatformRejectsSymlinkCreation() {
        val capability =
            LinkCapabilityProbe.probeForTest { _, _ ->
                throw UnsupportedOperationException("symlink unavailable")
            }

        assertEquals(LinkCapability.Unavailable, capability)
    }

    @Test
    fun reportsDirectCapabilityWhenPlatformCreatesDirectorySymlinks() {
        val capability =
            LinkCapabilityProbe.probeForTest { link: Path, target: Path ->
                java.nio.file.Files
                    .createSymbolicLink(link, target)
            }

        assumeTrue(capability is LinkCapability.DirectSymlink)
        assertEquals(LinkCapability.DirectSymlink, capability)
    }
}
