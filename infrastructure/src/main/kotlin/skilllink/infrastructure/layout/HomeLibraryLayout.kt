package skilllink.infrastructure.layout

import skilllink.application.ports.LibraryLayoutPort
import skilllink.application.ports.SkillLinkLayout
import java.nio.file.Files
import java.nio.file.Path

class HomeLibraryLayout(
    private val homeOverride: Path? = null,
) : LibraryLayoutPort {
    override fun resolve(): SkillLinkLayout {
        val home = homeOverride ?: Path.of(System.getProperty("user.home"))
        val root = home.resolve(".skilllink")
        return SkillLinkLayout(
            root = root,
            skillsRoot = root.resolve("skills"),
            databasePath = root.resolve("skilllink.db"),
            diagnosticsRoot = root.resolve("diagnostics"),
            lockPath = root.resolve(".writer.lock"),
        )
    }

    override fun isInitialized(): Boolean {
        val layout = resolve()
        return Files.exists(layout.databasePath) || Files.exists(layout.skillsRoot)
    }
}
