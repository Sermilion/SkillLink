package skilllink.cli.exit

object CliExitCodes {
    const val SUCCESS = 0
    const val INVALID_ARGUMENTS = 2
    const val NOT_FOUND = 3
    const val CONFLICT = 4
    const val IO_OR_PLATFORM = 5
    const val CLEANUP_PENDING = 6
    const val CANCELLED = 130
}
