package skilllink.domain.library

import java.util.Locale

sealed interface SkillNameOutcome {
  data class Accepted(
    val displayName: String,
    val comparisonKey: NameComparisonKey,
  ) : SkillNameOutcome

  sealed interface Rejected : SkillNameOutcome {
    data object Empty : Rejected

    data object NonAscii : Rejected

    data object InvalidGrammar : Rejected

    data object WindowsReserved : Rejected
  }
}

object SkillNamePolicy {
  private const val MAX_NAME_LENGTH = 64
  private const val ASCII_LIMIT = 0x7F

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

  fun validate(candidate: String): SkillNameOutcome =
    when {
      candidate.isEmpty() -> SkillNameOutcome.Rejected.Empty
      candidate.any { it.code > ASCII_LIMIT } -> SkillNameOutcome.Rejected.NonAscii
      else -> validatePortableName(candidate)
    }

  private fun validatePortableName(candidate: String): SkillNameOutcome {
    val lowered = candidate.lowercase(Locale.ROOT)
    return when {
      lowered.length !in 1..MAX_NAME_LENGTH || !portablePattern.matches(lowered) -> {
        SkillNameOutcome.Rejected.InvalidGrammar
      }

      windowsReserved.contains(lowered) -> {
        SkillNameOutcome.Rejected.WindowsReserved
      }

      else -> {
        SkillNameOutcome.Accepted(candidate, NameComparisonKey(lowered))
      }
    }
  }

  fun comparisonKeyForLookup(input: String): NameComparisonKey? =
    when (val outcome = validate(input)) {
      is SkillNameOutcome.Accepted -> outcome.comparisonKey
      is SkillNameOutcome.Rejected -> null
    }
}
