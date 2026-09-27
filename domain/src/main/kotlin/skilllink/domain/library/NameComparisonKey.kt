package skilllink.domain.library

@JvmInline
value class NameComparisonKey(
  val value: String,
) {
  init {
    require(value.isNotEmpty()) { "name key required" }
  }
}
