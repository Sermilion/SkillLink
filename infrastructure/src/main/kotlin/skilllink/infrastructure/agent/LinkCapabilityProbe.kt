package skilllink.infrastructure.agent

import skilllink.application.ports.LinkCapability
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists

private typealias LinkCreator = (Path, Path) -> Unit

object LinkCapabilityProbe {
  private val cached: LinkCapability by lazy { probe() }

  fun current(): LinkCapability = cached

  internal fun probeForTest(createLink: LinkCreator): LinkCapability = probe(createLink)

  private fun probe(): LinkCapability = probe { link, target -> Files.createSymbolicLink(link, target) }

  private fun probe(createLink: LinkCreator): LinkCapability {
    val dir = createTempDirectory("skilllink-symlink-probe")
    val target = dir.resolve("target")
    val link = dir.resolve("link")
    try {
      Files.createDirectory(target)
      createLink(link, target)
      return LinkCapability.DirectSymlink
    } catch (_: Exception) {
      return LinkCapability.Unavailable
    } finally {
      link.deleteIfExists()
      target.deleteIfExists()
      dir.deleteIfExists()
    }
  }
}
