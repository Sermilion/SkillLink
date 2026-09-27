package skilllink.cli.render

import skilllink.application.installation.model.ManagedSkillSnapshot

private const val CONTROL_CHAR_LIMIT = 32
private const val DELETE_CHAR = 127

internal fun formatRows(skills: List<ManagedSkillSnapshot>): String {
    val builder = StringBuilder()
    skills.forEachIndexed { index, skill ->
        builder.append(
            "${index + 1}. ${escape(skill.displayName)}  " +
                "${escape(skill.canonicalPath.toString())}\n",
        )
        skill.installations.forEach { installation ->
            builder.append(
                "   ${installation.agent.wireValue}: desired=${installation.desired.name.lowercase()} " +
                    "observed=${installation.observed.name.lowercase()} " +
                    "${escape(installation.destination.toString())}\n",
            )
        }
    }
    return builder.toString()
}

private fun escape(value: String): String =
    buildString(value.length) {
        value.forEach { char ->
            if (char.code < CONTROL_CHAR_LIMIT || char.code == DELETE_CHAR) {
                append('?')
            } else {
                append(char)
            }
        }
    }
