package skilllink.domain.library

import java.util.UUID

@JvmInline
value class SkillId(
  val value: String,
) {
  init {
    require(value.isNotBlank()) { "skill id required" }
  }

  companion object {
    fun newId(): SkillId = SkillId(UUID.randomUUID().toString())
  }
}
