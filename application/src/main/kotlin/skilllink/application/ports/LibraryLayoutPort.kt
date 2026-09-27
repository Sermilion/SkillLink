package skilllink.application.ports

import java.nio.file.Path

data class SkillLinkLayout(
    val root: Path,
    val skillsRoot: Path,
    val trashRoot: Path,
    val databasePath: Path,
    val diagnosticsRoot: Path,
    val lockPath: Path,
)

interface LibraryLayoutPort {
    fun resolve(): SkillLinkLayout

    fun isInitialized(): Boolean
}
