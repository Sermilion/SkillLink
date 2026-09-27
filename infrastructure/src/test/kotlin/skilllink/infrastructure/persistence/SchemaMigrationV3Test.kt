package skilllink.infrastructure.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.Statement

class SchemaMigrationV3Test {
    @TempDir
    lateinit var temp: Path

    @Test
    fun migratesPopulatedSubtaskOneDatabaseToVersionFour() {
        val db = temp.resolve("subtask1.db")
        createPopulatedSubtaskOneDatabase(db)

        val store = SqliteCatalogStore(db)
        val skill = store.listActiveOrdered().single()
        assertEquals(SkillId("skill-1"), skill.id)
        assertEquals(DesiredInstallationState.Enabled, skill.installations.single().desired)
        assertNotNull(store.findActiveByNameKey(NameComparisonKey("demo")))
        assertMigratedSchema(db)
    }
}

private fun createPopulatedSubtaskOneDatabase(db: Path) {
    DriverManager.getConnection("jdbc:sqlite:$db").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)")
            statement.execute("INSERT INTO schema_version VALUES (3)")
            statement.execute(
                """
                CREATE TABLE skills (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    name_key TEXT NOT NULL UNIQUE,
                    canonical_path TEXT NOT NULL,
                    active INTEGER NOT NULL,
                    cleanup_pending INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE installations (
                    skill_id TEXT NOT NULL,
                    agent TEXT NOT NULL,
                    destination TEXT NOT NULL,
                    desired TEXT NOT NULL,
                    observed TEXT NOT NULL,
                    PRIMARY KEY (skill_id, agent)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE operations (
                    id TEXT PRIMARY KEY,
                    skill_id TEXT NOT NULL,
                    comparison_key TEXT NOT NULL,
                    phase TEXT NOT NULL,
                    staging_path TEXT,
                    canonical_path TEXT,
                    staging_identity TEXT,
                    canonical_identity TEXT,
                    source_root TEXT,
                    source_fingerprint TEXT,
                    agent_destinations TEXT
                )
                """.trimIndent(),
            )
            insertSubtaskOneRows(statement)
        }
    }
}

private fun insertSubtaskOneRows(statement: Statement) {
    statement.execute("INSERT INTO skills VALUES ('skill-1', 'demo', 'demo', '/skills/demo', 1, 0)")
    statement.execute(
        "INSERT INTO installations VALUES ('skill-1', 'claude', '/agents/claude/demo', 'Enabled', 'Linked')",
    )
    statement.execute(
        """
        INSERT INTO operations
        VALUES ('op-1', 'skill-1', 'demo', 'Completed', NULL, '/skills/demo', NULL, NULL, NULL, 'fp', '')
        """.trimIndent(),
    )
}

private fun assertMigratedSchema(db: Path) {
    DriverManager.getConnection("jdbc:sqlite:$db").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT version FROM schema_version").use { result ->
                result.next()
                assertEquals(4, result.getInt(1))
            }
            statement
                .executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='trash_records'",
                ).use { result ->
                    assertTrue(result.next())
                }
            statement.executeQuery("SELECT id, comparison_key FROM operations WHERE id = 'op-1'").use { result ->
                assertTrue(result.next())
                assertEquals("op-1", result.getString("id"))
                assertEquals("demo", result.getString("comparison_key"))
            }
        }
    }
}
