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
    }
}

object FrontmatterReader {
    private const val FRONTMATTER_START = 3

    fun parse(content: String): FrontmatterOutcome =
        when (val block = frontmatterBlock(content)) {
            null -> {
                FrontmatterOutcome.Rejected.MissingDelimiter
            }

            else -> {
                if (hasUnsafeConstruct(block)) {
                    FrontmatterOutcome.Rejected.UnsafeConstruct
                } else {
                    when (val fields = readFields(block)) {
                        is FieldReadOutcome.Rejected -> fields.reason
                        is FieldReadOutcome.Parsed -> fields.toOutcome()
                        else -> FrontmatterOutcome.Rejected.Malformed
                    }
                }
            }
        }

    private fun frontmatterBlock(content: String): String? {
        if (!content.startsWith("---")) {
            return null
        }
        val end = content.indexOf("\n---", startIndex = FRONTMATTER_START)
        return if (end < 0) null else content.substring(FRONTMATTER_START, end).trim()
    }

    private fun hasUnsafeConstruct(block: String): Boolean = listOf("!!", "&", "*", "<<").any(block::contains)

    private fun readFields(block: String): FieldReadOutcome {
        var name: String? = null
        var description: String? = null
        var nameCount = 0
        var descriptionCount = 0
        var rejection: FrontmatterOutcome.Rejected? = null
        for (line in block.lineSequence()) {
            val trimmed = line.trim()
            if (rejection == null) {
                when (val field = readField(trimmed)) {
                    FieldReadOutcome.Skip -> {}

                    is FieldReadOutcome.Rejected -> {
                        rejection = field.reason
                    }

                    is FieldReadOutcome.Name -> {
                        nameCount++
                        name = field.value
                    }

                    is FieldReadOutcome.Description -> {
                        descriptionCount++
                        description = field.value
                    }

                    is FieldReadOutcome.Parsed -> {
                        return field
                    }
                }
            }
        }
        return when {
            rejection != null -> FieldReadOutcome.Rejected(rejection)
            nameCount > 1 -> FieldReadOutcome.Rejected(FrontmatterOutcome.Rejected.DuplicateName)
            descriptionCount > 1 -> FieldReadOutcome.Rejected(FrontmatterOutcome.Rejected.DuplicateDescription)
            name == null -> FieldReadOutcome.Rejected(FrontmatterOutcome.Rejected.MissingName)
            description.isNullOrEmpty() -> FieldReadOutcome.Rejected(FrontmatterOutcome.Rejected.MissingDescription)
            else -> FieldReadOutcome.Parsed(name, description)
        }
    }

    private fun readField(trimmed: String): FieldReadOutcome =
        when {
            trimmed.isEmpty() || trimmed.startsWith("#") -> {
                FieldReadOutcome.Skip
            }

            trimmed.indexOf(':') <= 0 -> {
                FieldReadOutcome.Rejected(FrontmatterOutcome.Rejected.Malformed)
            }

            else -> {
                val colon = trimmed.indexOf(':')
                val rawValue = trimmed.substring(colon + 1).trim()
                if (rawValue.startsWith("{") || rawValue.startsWith("[") || rawValue.contains("\n")) {
                    FieldReadOutcome.Rejected(FrontmatterOutcome.Rejected.UnsafeConstruct)
                } else {
                    when (trimmed.substring(0, colon).trim()) {
                        "name" -> FieldReadOutcome.Name(rawValue.unquote())
                        "description" -> FieldReadOutcome.Description(rawValue.unquote())
                        else -> FieldReadOutcome.Skip
                    }
                }
            }
        }
}

private sealed interface FieldReadOutcome {
    data object Skip : FieldReadOutcome

    data class Name(
        val value: String,
    ) : FieldReadOutcome

    data class Description(
        val value: String,
    ) : FieldReadOutcome

    data class Parsed(
        val name: String?,
        val description: String?,
    ) : FieldReadOutcome {
        fun toOutcome(): FrontmatterOutcome = FrontmatterOutcome.Parsed(name.orEmpty(), description.orEmpty())
    }

    data class Rejected(
        val reason: FrontmatterOutcome.Rejected,
    ) : FieldReadOutcome
}

private fun String.unquote(): String = removeSurrounding("\"").removeSurrounding("'")
