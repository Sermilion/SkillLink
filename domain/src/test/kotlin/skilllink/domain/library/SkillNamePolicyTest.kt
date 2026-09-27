package skilllink.domain.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkillNamePolicyTest {
    @Test
    fun normalizesLookupKeyToLowercaseAscii() {
        val outcome = SkillNamePolicy.validate("Code-Review")
        assertTrue(outcome is SkillNameOutcome.Accepted)
        assertEquals("code-review", (outcome as SkillNameOutcome.Accepted).comparisonKey.value)
        assertEquals("code-review", SkillNamePolicy.comparisonKeyForLookup("CODE-REVIEW")?.value)
    }

    @Test
    fun rejectsNonAsciiNames() {
        assertTrue(SkillNamePolicy.validate("café") is SkillNameOutcome.Rejected.NonAscii)
    }

    @Test
    fun rejectsWindowsReservedNames() {
        assertTrue(SkillNamePolicy.validate("CON") is SkillNameOutcome.Rejected.WindowsReserved)
    }

    @Test
    fun rejectsInvalidGrammar() {
        assertTrue(SkillNamePolicy.validate("-bad") is SkillNameOutcome.Rejected.InvalidGrammar)
        assertTrue(SkillNamePolicy.validate("a--b") is SkillNameOutcome.Rejected.InvalidGrammar)
    }
}
