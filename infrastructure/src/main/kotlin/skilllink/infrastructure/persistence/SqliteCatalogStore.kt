package skilllink.infrastructure.persistence

import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.application.ports.OperationRecord
import skilllink.domain.agent.AgentId
import skilllink.domain.library.NameComparisonKey
import skilllink.domain.library.SkillId
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64

class SqliteCatalogStore(
    private val databasePath: Path,
) : CatalogPort, OperationJournalPort {
    @Volatile
    private var schemaReady = false

    override fun findActiveByNameKey(key: NameComparisonKey): SkillId? =
        openConnection().use { connection ->
            connection.prepareStatement(
                "SELECT id FROM skills WHERE name_key = ? AND active = 1",
            ).use { statement ->
                statement.setString(1, key.value)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        SkillId(result.getString("id"))
                    } else {
                        null
                    }
                }
            }
        }

    override fun listActiveOrdered(): List<ManagedSkillSnapshot> =
        openConnection().use { connection ->
            val skills = mutableListOf<ManagedSkillSnapshot>()
            connection.prepareStatement(
                """
                SELECT id, display_name, name_key, canonical_path
                FROM skills
                WHERE active = 1
                ORDER BY name_key ASC
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        val id = SkillId(result.getString("id"))
                        val installations = loadInstallations(connection, id)
                        skills.add(
                            ManagedSkillSnapshot(
                                id = id,
                                displayName = result.getString("display_name"),
                                comparisonKey = NameComparisonKey(result.getString("name_key")),
                                canonicalPath = Path.of(result.getString("canonical_path")),
                                installations = installations,
                            ),
                        )
                    }
                }
            }
            skills
        }

    override fun reserveSkill(
        id: SkillId,
        displayName: String,
        comparisonKey: NameComparisonKey,
        canonicalPath: Path,
        agents: Map<AgentId, Path>,
    ) {
        openConnection().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO skills (id, display_name, name_key, canonical_path, active, cleanup_pending)
                    VALUES (?, ?, ?, ?, 0, 0)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id.value)
                    statement.setString(2, displayName)
                    statement.setString(3, comparisonKey.value)
                    statement.setString(4, canonicalPath.toString())
                    statement.executeUpdate()
                }
                for ((agent, destination) in agents) {
                    connection.prepareStatement(
                        """
                        INSERT INTO installations (skill_id, agent, destination, desired, observed)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, id.value)
                        statement.setString(2, agent.wireValue)
                        statement.setString(3, destination.toString())
                        statement.setString(4, DesiredInstallationState.Enabled.name)
                        statement.setString(5, ObservedLinkCondition.Linked.name)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun commitOperation(operationId: String) {
        openConnection().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "UPDATE operations SET phase = ? WHERE id = ?",
                ).use { statement ->
                    statement.setString(1, OperationPhase.Committed.name)
                    statement.setString(2, operationId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE skills
                    SET active = 1
                    WHERE id = (SELECT skill_id FROM operations WHERE id = ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, operationId)
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun markCleanupPending(skillId: SkillId) {
        openConnection().use { connection ->
            connection.prepareStatement(
                "UPDATE skills SET cleanup_pending = 1 WHERE id = ?",
            ).use { statement ->
                statement.setString(1, skillId.value)
                statement.executeUpdate()
            }
        }
    }

    override fun clearReservation(skillId: SkillId) {
        openConnection().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement("DELETE FROM installations WHERE skill_id = ?").use {
                    it.setString(1, skillId.value)
                    it.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM skills WHERE id = ?").use {
                    it.setString(1, skillId.value)
                    it.executeUpdate()
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun updateObservedCondition(
        skillId: SkillId,
        agent: AgentId,
        observed: ObservedLinkCondition,
    ) {
        openConnection().use { connection ->
            connection.prepareStatement(
                "UPDATE installations SET observed = ? WHERE skill_id = ? AND agent = ?",
            ).use { statement ->
                statement.setString(1, observed.name)
                statement.setString(2, skillId.value)
                statement.setString(3, agent.wireValue)
                statement.executeUpdate()
            }
        }
    }

    override fun recordInstallationIntent(
        skillId: SkillId,
        agent: AgentId,
        destination: Path,
        desired: DesiredInstallationState,
    ) {
        openConnection().use { connection ->
            connection.prepareStatement(
                """
                UPDATE installations
                SET destination = ?, desired = ?
                WHERE skill_id = ? AND agent = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, destination.toString())
                statement.setString(2, desired.name)
                statement.setString(3, skillId.value)
                statement.setString(4, agent.wireValue)
                statement.executeUpdate()
            }
        }
    }

    override fun beginInstall(
        operationId: String,
        skillId: SkillId,
        comparisonKey: String,
        sourceRoot: Path,
        sourceFingerprint: String,
        agents: Map<AgentId, Path>,
    ) {
        openConnection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO operations (
                    id, skill_id, comparison_key, phase, source_root, source_fingerprint, agent_destinations
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, operationId)
                statement.setString(2, skillId.value)
                statement.setString(3, comparisonKey)
                statement.setString(4, OperationPhase.Staging.name)
                statement.setString(5, sourceRoot.toString())
                statement.setString(6, sourceFingerprint)
                statement.setString(
                    7,
                    agents.entries.joinToString(";") {
                        "${it.key.wireValue}=${encodePath(it.value)}"
                    },
                )
                statement.executeUpdate()
            }
        }
    }

    override fun updatePhase(operationId: String, phase: OperationPhase, paths: OperationPaths) {
        openConnection().use { connection ->
            connection.prepareStatement(
                """
                UPDATE operations
                SET phase = ?, staging_path = ?, canonical_path = ?,
                    staging_identity = ?, canonical_identity = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, phase.name)
                statement.setString(2, paths.stagingPath?.toString())
                statement.setString(3, paths.canonicalPath?.toString())
                statement.setString(4, paths.stagingIdentity)
                statement.setString(5, paths.canonicalIdentity)
                statement.setString(6, operationId)
                statement.executeUpdate()
            }
        }
    }

    override fun findIncomplete(): List<OperationRecord> =
        openConnection().use { connection ->
            val records = mutableListOf<OperationRecord>()
            connection.prepareStatement(
                """
                SELECT id, skill_id, comparison_key, phase, staging_path, canonical_path,
                    staging_identity, canonical_identity, source_root, source_fingerprint,
                    agent_destinations
                FROM operations
                WHERE phase NOT IN (?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, OperationPhase.Completed.name)
                statement.setString(2, OperationPhase.RolledBack.name)
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        val agents = parseAgents(result.getString("agent_destinations"))
                        records.add(
                            OperationRecord(
                                operationId = result.getString("id"),
                                skillId = SkillId(result.getString("skill_id")),
                                comparisonKey = result.getString("comparison_key"),
                                phase = OperationPhase.valueOf(result.getString("phase")),
                                stagingPath = result.getString("staging_path")?.let(Path::of),
                                canonicalPath = result.getString("canonical_path")?.let(Path::of),
                                stagingIdentity = result.getString("staging_identity"),
                                canonicalIdentity = result.getString("canonical_identity"),
                                sourceRoot = result.getString("source_root")?.let(Path::of),
                                sourceFingerprint = result.getString("source_fingerprint"),
                                agentDestinations = agents,
                            ),
                        )
                    }
                }
            }
            records
        }

    override fun markRolledBack(operationId: String) {
        updatePhase(operationId, OperationPhase.RolledBack, OperationPaths())
    }

    override fun markCommitted(operationId: String) {
        updatePhase(operationId, OperationPhase.Committed, OperationPaths())
    }

    override fun markCompleted(operationId: String) {
        updatePhase(operationId, OperationPhase.Completed, OperationPaths())
    }

    private fun loadInstallations(connection: Connection, skillId: SkillId): List<AgentInstallationSnapshot> {
        val installations = mutableListOf<AgentInstallationSnapshot>()
        connection.prepareStatement(
            "SELECT agent, destination, desired, observed FROM installations WHERE skill_id = ?",
        ).use { statement ->
            statement.setString(1, skillId.value)
            statement.executeQuery().use { result ->
                while (result.next()) {
                    val agent = AgentId.fromWire(result.getString("agent")) ?: continue
                    installations.add(
                        AgentInstallationSnapshot(
                            agent = agent,
                            destination = Path.of(result.getString("destination")),
                            desired = DesiredInstallationState.valueOf(result.getString("desired")),
                            observed = ObservedLinkCondition.valueOf(result.getString("observed")),
                        ),
                    )
                }
            }
        }
        return installations
    }

    private fun parseAgents(serialized: String): Map<AgentId, Path> {
        if (serialized.isBlank()) {
            return emptyMap()
        }
        return serialized.split(";").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size != 2) {
                return@mapNotNull null
            }
            val agent = AgentId.fromWire(parts[0]) ?: return@mapNotNull null
            agent to Path.of(decodePath(parts[1]))
        }.toMap()
    }

    private fun encodePath(path: Path): String =
        "b64:" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                path.toString().toByteArray(StandardCharsets.UTF_8),
            )

    private fun decodePath(value: String): String =
        if (!value.startsWith("b64:")) {
            value
        } else {
            try {
                String(Base64.getUrlDecoder().decode(value.removePrefix("b64:")), StandardCharsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                value
            }
        }

    private fun openConnection(): Connection {
        FilesSupport.ensureParent(databasePath)
        val connection = DriverManager.getConnection("jdbc:sqlite:${databasePath}")
        if (!schemaReady) {
            synchronized(this) {
                if (!schemaReady) {
                    try {
                        SchemaMigrator.migrate(connection)
                        schemaReady = true
                    } catch (error: Exception) {
                        connection.close()
                        throw error
                    }
                }
            }
        }
        return connection
    }
}

private object FilesSupport {
    fun ensureParent(path: Path) {
        val parent = path.parent
        if (parent != null) {
            java.nio.file.Files.createDirectories(parent)
        }
    }
}

private object SchemaMigrator {
    private const val SCHEMA_VERSION = 3

    fun migrate(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS skills (
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
                CREATE TABLE IF NOT EXISTS installations (
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
                CREATE TABLE IF NOT EXISTS operations (
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
        }
        val version =
            connection.prepareStatement("SELECT version FROM schema_version LIMIT 1").use { statement ->
                statement.executeQuery().use { result ->
                    if (result.next()) result.getInt("version") else null
                }
            }
        if (version == null) {
            connection.prepareStatement("INSERT INTO schema_version (version) VALUES (?)").use {
                it.setInt(1, SCHEMA_VERSION)
                it.executeUpdate()
            }
        } else if (version == 1) {
            connection.createStatement().use { statement ->
                statement.execute("ALTER TABLE operations ADD COLUMN source_fingerprint TEXT")
                statement.execute("ALTER TABLE operations ADD COLUMN staging_identity TEXT")
                statement.execute("ALTER TABLE operations ADD COLUMN canonical_identity TEXT")
                statement.execute("UPDATE schema_version SET version = $SCHEMA_VERSION")
            }
        } else if (version == 2) {
            connection.createStatement().use { statement ->
                statement.execute("ALTER TABLE operations ADD COLUMN staging_identity TEXT")
                statement.execute("ALTER TABLE operations ADD COLUMN canonical_identity TEXT")
                statement.execute("UPDATE schema_version SET version = $SCHEMA_VERSION")
            }
        } else if (version != SCHEMA_VERSION) {
            throw IllegalStateException("unsupported schema version $version")
        }
    }
}
