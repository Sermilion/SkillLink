package skilllink.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import skilllink.cli.exit.CliExitCodes
import java.nio.file.Path

class SkillLinkCliTest {
  @Test
  fun mapsInvalidStorageInitializationToIoFailureForList() {
    val cli =
      SkillLinkCli(
        installOperationProvider = { error("install is not used") },
        listOperationProvider = { error("invalid database") },
        manageOperationProvider = { error("manage is not used") },
        openOperationProvider = { error("open is not used") },
        workingDirectory = Path.of("/tmp"),
      )

    val outcome = cli.run(arrayOf("list"))

    assertEquals(CliExitCodes.IO_OR_PLATFORM, outcome.exitCode)
  }
}
