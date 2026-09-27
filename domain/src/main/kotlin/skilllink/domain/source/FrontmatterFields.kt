package skilllink.domain.source

sealed interface FrontmatterOutcome {
    data class Parsed(val name: String, val description: String) : FrontmatterOutcome

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
    fun parse(content: String): FrontmatterOutcome {
        if (!content.startsWith("---")) {
            return FrontmatterOutcome.Rejected.MissingDelimiter
        }
        val end = content.indexOf("\n---", startIndex = 3)
        if (end < 0) {
            return FrontmatterOutcome.Rejected.MissingDelimiter
        }
        val block = content.substring(3, end).trim()
        if (block.contains("!!") || block.contains("&") || block.contains("*") || block.contains("<<")) {
            return FrontmatterOutcome.Rejected.UnsafeConstruct
        }
        var name: String? = null
        var description: String? = null
        var nameCount = 0
        var descriptionCount = 0
        for (line in block.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue
            }
            val colon = trimmed.indexOf(':')
            if (colon <= 0) {
                return FrontmatterOutcome.Rejected.Malformed
            }
            val key = trimmed.substring(0, colon).trim()
            val rawValue = trimmed.substring(colon + 1).trim()
            if (rawValue.startsWith("{") || rawValue.startsWith("[") || rawValue.contains("\n")) {
                return FrontmatterOutcome.Rejected.UnsafeConstruct
            }
            val value = rawValue.removeSurrounding("\"").removeSurrounding("'")
            when (key) {
                "name" -> {
                    nameCount++
                    name = value
                }
                "description" -> {
                    descriptionCount++
                    description = value
                }
            }
        }
        if (nameCount > 1) {
            return FrontmatterOutcome.Rejected.DuplicateName
        }
        if (descriptionCount > 1) {
            return FrontmatterOutcome.Rejected.DuplicateDescription
        }
        val resolvedName = name ?: return FrontmatterOutcome.Rejected.MissingName
        val resolvedDescription = description ?: return FrontmatterOutcome.Rejected.MissingDescription
        if (resolvedDescription.isEmpty()) {
            return FrontmatterOutcome.Rejected.MissingDescription
        }
        return FrontmatterOutcome.Parsed(resolvedName, resolvedDescription)
    }
}
