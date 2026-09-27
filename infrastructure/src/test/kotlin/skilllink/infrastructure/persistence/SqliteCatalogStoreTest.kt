package skilllink.infrastructure.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.ports.OperationPhase
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import java.nio.file.Path
import java.sql.DriverManager

class SqliteCatalogStoreTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun persistsSkillIdentityNameKeyAndInstallations() {
        val db = temp.resolve("skilllink.db")
        val store = SqliteCatalogStore(db)
        val id = SkillId.newId()
        val canonical = temp.resolve("skills/demo")
        val destination = temp.resolve("agents/claude/demo")
        val operationId = "operation-1"
        store.beginInstall(
            operationId,
            id,
            "demo",
            InstallTestInput(
                temp.resolve("source/demo"),
                "fingerprint",
                mapOf(AgentId.Claude to destination),
            ),
        )
        store.reserveSkill(
            id,
            "demo",
            NameComparisonKey("demo"),
            canonical,
            mapOf(AgentId.Claude to destination),
        )
        store.commitOperation(operationId)
        val loaded = store.listActiveOrdered().single()
        assertEquals(id, loaded.id)
        assertEquals("demo", loaded.comparisonKey.value)
        assertEquals(canonical, loaded.canonicalPath)
        assertEquals(1, loaded.installations.size)
        assertEquals(DesiredInstallationState.Enabled, loaded.installations.single().desired)
        assertEquals(destination, loaded.installations.single().destination)
        assertEquals(ObservedLinkCondition.Linked, loaded.installations.single().observed)
        assertNotNull(store.findActiveByNameKey(NameComparisonKey("demo")))
        val operation = store.findIncomplete().single()
        assertEquals(OperationPhase.Committed, operation.phase)
        assertEquals("fingerprint", operation.sourceFingerprint)
        store.markCompleted(operationId)
        assertEquals(0, store.findIncomplete().size)
    }

    @Test
    fun migratesVersionOneOperationWithoutRecreatingData() {
        val db = temp.resolve("legacy.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)")
                statement.execute("INSERT INTO schema_version VALUES (1)")
                statement.execute(
                    """
                    CREATE TABLE operations (
                        id TEXT PRIMARY KEY,
                        skill_id TEXT NOT NULL,
                        comparison_key TEXT NOT NULL,
                        phase TEXT NOT NULL,
                        staging_path TEXT,
                        canonical_path TEXT,
                        source_root TEXT,
                        agent_destinations TEXT
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO operations
                    VALUES ('op', 'skill', 'demo', 'Staging', NULL, NULL, '/source', '')
                    """.trimIndent(),
                )
            }
        }

        val store = SqliteCatalogStore(db)
        val record = store.findIncomplete().single()

        assertEquals("op", record.operationId)
        assertEquals(null, record.sourceFingerprint)
    }

    @Test
    fun rejectsAnIncompatibleSchemaWithoutRecreatingTheDatabase() {
        val db = temp.resolve("future.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)")
                statement.execute("INSERT INTO schema_version VALUES (99)")
            }
        }

        val store = SqliteCatalogStore(db)

        assertThrows(IllegalStateException::class.java) {
            store.listActiveOrdered()
        }
        assertThrows(IllegalStateException::class.java) {
            store.beginInstall(
                "blocked-operation",
                SkillId("blocked"),
                "blocked",
                InstallTestInput(
                    temp.resolve("source/blocked"),
                    "fingerprint",
                    emptyMap(),
                ),
            )
        }
        DriverManager.getConnection("jdbc:sqlite:$db").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version FROM schema_version").use { result ->
                    result.next()
                    assertEquals(99, result.getInt(1))
                }
                statement
                    .executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='skills'",
                    ).use { result ->
                        assertEquals(false, result.next())
                    }
            }
        }
    }

    @Test
    fun failedMigrationPreservesOriginalStateAndBlocksMutation() {
        val db = temp.resolve("failed-migration.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)")
                statement.execute("INSERT INTO schema_version VALUES (3)")
                statement.execute(
                    """
                    CREATE TABLE operations (
                        id TEXT PRIMARY KEY,
                        skill_id TEXT NOT NULL,
                        comparison_key TEXT NOT NULL,
                        phase TEXT NOT NULL,
                        source_fingerprint TEXT,
                        operation_kind TEXT NOT NULL DEFAULT 'Install'
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    "INSERT INTO operations VALUES ('existing', 'skill', 'demo', 'Staging', 'fingerprint', 'Install')",
                )
            }
        }

        val store = SqliteCatalogStore(db)
        assertThrows(IllegalStateException::class.java) {
            store.findIncomplete()
        }
        assertThrows(IllegalStateException::class.java) {
            store.beginInstall(
                "blocked-after-failure",
                SkillId("blocked"),
                "blocked",
                InstallTestInput(
                    temp.resolve("source/blocked"),
                    "fingerprint",
                    emptyMap(),
                ),
            )
        }
        DriverManager.getConnection("jdbc:sqlite:$db").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version FROM schema_version").use { result ->
                    result.next()
                    assertEquals(3, result.getInt(1))
                }
                statement.executeQuery("SELECT id, source_fingerprint FROM operations").use { result ->
                    assertEquals(true, result.next())
                    assertEquals("existing", result.getString("id"))
                    assertEquals("fingerprint", result.getString("source_fingerprint"))
                }
                statement
                    .executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='skills'",
                    ).use { result ->
                        assertEquals(false, result.next())
                    }
            }
        }
    }
}
