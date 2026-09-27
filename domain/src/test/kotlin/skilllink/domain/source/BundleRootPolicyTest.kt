package skilllink.domain.source

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BundleRootPolicyTest {
    @Test
    fun rejectsFilesystemHomeAndSkillLinkRoots() {
        val home = "/home/tester"
        val data = "$home/.skilllink"
        val agent = "$home/.cursor/skills"

        assertTrue(
            BundleRootPolicy.validateRoot("/", data, setOf(agent)) is
                BundleValidationOutcome.Rejected.UnsafeRoot,
        )
        assertTrue(
            BundleRootPolicy.validateRoot(home, data, setOf(agent)) is BundleValidationOutcome.Rejected.UnsafeRoot,
        )
        assertTrue(
            BundleRootPolicy.validateRoot("$data/source", data, setOf(agent)) is
                BundleValidationOutcome.Rejected.InsideSkillLinkData,
        )
        assertTrue(
            BundleRootPolicy.validateRoot("$agent/source", data, setOf(agent)) is
                BundleValidationOutcome.Rejected.InsideAgentRoot,
        )
    }

    @Test
    fun rejectsRepositoryPaths() {
        val outcome = BundleRootPolicy.validateRoot("/work/.git/skills", "/home/tester/.skilllink", emptySet())

        assertTrue(outcome is BundleValidationOutcome.Rejected.RepositoryRoot)
    }
}
