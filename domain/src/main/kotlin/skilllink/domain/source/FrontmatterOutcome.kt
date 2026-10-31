package skilllink.domain.source

sealed interface FrontmatterOutcome {
  data class Parsed(
    val name: String,
    val description: String,
  ) : FrontmatterOutcome

  sealed interface Rejected : FrontmatterOutcome {
    data object MissingDelimiter : Rejected

    data object DuplicateName : Rejected

    data object DuplicateDescription : Rejected

    data object MissingName : Rejected

    data object MissingDescription : Rejected

    data object Malformed : Rejected

    data object UnsafeConstruct : Rejected

    data object InputLimit : Rejected
  }
}
