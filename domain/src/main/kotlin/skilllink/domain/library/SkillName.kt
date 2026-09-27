package skilllink.domain.library

import java.util.Locale

sealed interface SkillNameOutcome {
    data class Accepted(val displayName: String, val comparisonKey: NameComparisonKey) : SkillNameOutcome

    sealed interface Rejected : SkillNameOutcome {
        data object Empty : Rejected

        data object NonAscii : Rejected

        data object InvalidGrammar : Rejected

        data object WindowsReserved : Rejected
    }
}

object SkillNamePolicy {
    private val portablePattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private val windowsReserved =
        setOf(
            "con",
            "prn",
            "aux",
            "nul",
            "com1",
            "com2",
            "com3",
            "com4",
            "com5",
            "com6",
            "com7",
            "com8",
            "com9",
            "lpt1",
            "lpt2",
            "lpt3",
            "lpt4",
            "lpt5",
            "lpt6",
            "lpt7",
            "lpt8",
            "lpt9",
        )

    fun validate(candidate: String): SkillNameOutcome {
        if (candidate.isEmpty()) {
            return SkillNameOutcome.Rejected.Empty
        }
        if (candidate.any { it.code > 0x7F }) {
            return SkillNameOutcome.Rejected.NonAscii
        }
        val lowered = candidate.lowercase(Locale.ROOT)
        if (lowered.length !in 1..64 || !portablePattern.matches(lowered)) {
            return SkillNameOutcome.Rejected.InvalidGrammar
        }
        if (windowsReserved.contains(lowered)) {
            return SkillNameOutcome.Rejected.WindowsReserved
        }
        return SkillNameOutcome.Accepted(displayName = candidate, comparisonKey = NameComparisonKey(lowered))
    }

    fun comparisonKeyForLookup(input: String): NameComparisonKey? =
        when (val outcome = validate(input)) {
            is SkillNameOutcome.Accepted -> outcome.comparisonKey
            is SkillNameOutcome.Rejected -> null
        }
}
