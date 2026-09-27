package skilllink.domain.source

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrontmatterReaderTest {
    @Test
    fun parsesScalarNameAndDescription() {
        val content =
            """
            ---
            name: demo-skill
            description: Does a thing
            ---
            body
            """.trimIndent()
        val outcome = FrontmatterReader.parse(content)
        assertTrue(outcome is FrontmatterOutcome.Parsed)
    }

    @Test
    fun rejectsDuplicateNameKey() {
        val content =
            """
            ---
            name: one
            name: two
            description: text
            ---
            """.trimIndent()
        assertTrue(FrontmatterReader.parse(content) is FrontmatterOutcome.Rejected.DuplicateName)
    }

    @Test
    fun rejectsUnsafeConstructs() {
        val content =
            """
            ---
            name: !!evil
            description: text
            ---
            """.trimIndent()
        assertTrue(FrontmatterReader.parse(content) is FrontmatterOutcome.Rejected.UnsafeConstruct)
    }
}
