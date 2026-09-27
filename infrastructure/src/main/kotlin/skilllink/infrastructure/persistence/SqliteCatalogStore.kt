package skilllink.infrastructure.persistence

import skilllink.application.installation.model.AgentInstallationSnapshot
import skilllink.application.installation.model.DesiredInstallationState
import skilllink.application.installation.model.ManagedSkillSnapshot
import skilllink.application.installation.model.ObservedLinkCondition
import skilllink.application.ports.CatalogPort
import skilllink.application.ports.InstallOperationStart
import skilllink.application.ports.ManagementOperationStart
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.OperationKind
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
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Base64

internal object SqliteParameters {
    const val FIRST = 1
    const val SECOND = 2
    const val THIRD = 3
    const val FOURTH = 4
    const val FIFTH = 5
    const val SIXTH = 6
    const val SEVENTH = 7
    const val EIGHTH = 8
}

class SqliteCatalogStore(
    databasePath: Path,
) : CatalogPort,
    OperationJournalPort by SqliteOperationJournalDelegate(databasePath) {
    private val connectionProvider = SqliteConnectionProvider(databasePath)

    override fun findActiveByNameKey(key: NameComparisonKey): SkillId? =
        connectionProvider.openConnection().use { connection ->
            connection.query(
                "SELECT id FROM skills WHERE name_key = ? AND active = 1",
                { statement -> statement.setString(SqliteParameters.FIRST, key.value) },
            ) { result ->
                if (result.next()) SkillId(result.getString("id")) else null
            }
        }

    override fun listActiveOrdered(): List<ManagedSkillSnapshot> =
        connectionProvider.openConnection().use { connection ->
            val skills = mutableListOf<ManagedSkillSnapshot>()
            connection.query(
                """
                SELECT id, display_name, name_key, canonical_path
                FROM skills
                WHERE active = 1
                ORDER BY name_key ASC
                """.trimIndent(),
            ) { result ->
                while (result.next()) {
                    val id = SkillId(result.getString("id"))
                    val installations = SqliteCatalogHelpers.loadInstallations(connection, id)
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
            skills
        }

    override fun reserveSkill(
        id: SkillId,
        displayName: String,
        comparisonKey: NameComparisonKey,
        canonicalPath: Path,
        agents: Map<AgentId, Path>,
    ) {
        transaction { connection ->
            connection.execute(
                """
                INSERT INTO skills (id, display_name, name_key, canonical_path, active, cleanup_pending)
                VALUES (?, ?, ?, ?, 0, 0)
                """.trimIndent(),
            ) { statement ->
                statement.setString(SqliteParameters.FIRST, id.value)
                statement.setString(SqliteParameters.SECOND, displayName)
                statement.setString(SqliteParameters.THIRD, comparisonKey.value)
                statement.setString(SqliteParameters.FOURTH, canonicalPath.toString())
            }
            for ((agent, destination) in agents) {
                connection.execute(
                    """
                    INSERT INTO installations (skill_id, agent, destination, desired, observed)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ) { statement ->
                    statement.setString(SqliteParameters.FIRST, id.value)
                    statement.setString(SqliteParameters.SECOND, agent.wireValue)
                    statement.setString(SqliteParameters.THIRD, destination.toString())
                    statement.setString(SqliteParameters.FOURTH, DesiredInstallationState.Enabled.name)
                    statement.setString(SqliteParameters.FIFTH, ObservedLinkCondition.Linked.name)
                }
            }
        }
    }

    override fun commitOperation(operationId: String) {
        transaction { connection ->
            connection.execute("UPDATE operations SET phase = ? WHERE id = ?") { statement ->
                statement.setString(SqliteParameters.FIRST, OperationPhase.Committed.name)
                statement.setString(SqliteParameters.SECOND, operationId)
            }
            connection.execute(
                """
                UPDATE skills
                SET active = 1
                WHERE id = (SELECT skill_id FROM operations WHERE id = ?)
                """.trimIndent(),
            ) { statement ->
                statement.setString(SqliteParameters.FIRST, operationId)
            }
        }
    }

    override fun markCleanupPending(skillId: SkillId) {
        connectionProvider.openConnection().use { connection ->
            connection
                .prepareStatement(
                    "UPDATE skills SET cleanup_pending = 1 WHERE id = ?",
                ).use { statement ->
                    statement.setString(SqliteParameters.FIRST, skillId.value)
                    statement.executeUpdate()
                }
        }
    }

    override fun clearReservation(skillId: SkillId) {
        transaction { connection ->
            connection.execute("DELETE FROM installations WHERE skill_id = ?") {
                it.setString(SqliteParameters.FIRST, skillId.value)
            }
            connection.execute("DELETE FROM skills WHERE id = ?") {
                it.setString(SqliteParameters.FIRST, skillId.value)
            }
        }
    }

    override fun findActiveSnapshot(skillId: SkillId) = listActiveOrdered().find { it.id == skillId }

    override fun commitManagementState(
        operationId: String,
        skillId: SkillId,
        installations: List<AgentInstallationSnapshot>,
    ) {
        transaction { connection ->
            for (installation in installations) {
                connection.execute(
                    """
                    INSERT INTO installations (skill_id, agent, destination, desired, observed)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(skill_id, agent) DO UPDATE SET
                        destination = excluded.destination,
                        desired = excluded.desired,
                        observed = excluded.observed
                    """.trimIndent(),
                ) { statement ->
                    statement.setString(SqliteParameters.FIRST, skillId.value)
                    statement.setString(SqliteParameters.SECOND, installation.agent.wireValue)
                    statement.setString(SqliteParameters.THIRD, installation.destination.toString())
                    statement.setString(SqliteParameters.FOURTH, installation.desired.name)
                    statement.setString(SqliteParameters.FIFTH, installation.observed.name)
                }
            }
            connection.execute("UPDATE operations SET phase = ? WHERE id = ?") { statement ->
                statement.setString(SqliteParameters.FIRST, OperationPhase.Committed.name)
                statement.setString(SqliteParameters.SECOND, operationId)
            }
        }
    }

    override fun commitRemoval(
        operationId: String,
        skillId: SkillId,
        trashPath: Path,
        formerAgents: Set<AgentId>,
    ) {
        transaction { connection ->
            val skill = SqliteCatalogHelpers.loadRemovalSkill(connection, skillId)
            SqliteCatalogHelpers.insertTrashRecord(
                connection,
                TrashRecordInput(skillId, operationId, skill, trashPath, formerAgents),
            )
            connection.execute("DELETE FROM installations WHERE skill_id = ?") {
                it.setString(SqliteParameters.FIRST, skillId.value)
            }
            connection.execute("DELETE FROM skills WHERE id = ?") {
                it.setString(SqliteParameters.FIRST, skillId.value)
            }
            connection.execute("UPDATE operations SET phase = ?, trash_path = ? WHERE id = ?") {
                it.setString(SqliteParameters.FIRST, OperationPhase.Committed.name)
                it.setString(SqliteParameters.SECOND, trashPath.toString())
                it.setString(SqliteParameters.THIRD, operationId)
            }
        }
    }

    private inline fun <T> transaction(block: (Connection) -> T): T =
        connectionProvider.openConnection().use { connection ->
            connection.autoCommit = false
            try {
                block(connection).also { connection.commit() }
            } catch (error: SQLException) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
}

private inline fun <T> Connection.query(
    sql: String,
    bind: (PreparedStatement) -> Unit = {},
    read: (ResultSet) -> T,
): T =
    prepareStatement(sql).use { statement ->
        bind(statement)
        statement.executeQuery().use(read)
    }

private inline fun Connection.execute(
    sql: String,
    bind: (PreparedStatement) -> Unit = {},
): Int =
    prepareStatement(sql).use { statement ->
        bind(statement)
        statement.executeUpdate()
    }

internal data class TrashRecordInput(
    val skillId: SkillId,
    val operationId: String,
    val skill: Triple<String, String, String>,
    val trashPath: Path,
    val formerAgents: Set<AgentId>,
)

internal object SqliteCatalogHelpers {
    fun loadRemovalSkill(
        connection: Connection,
        skillId: SkillId,
    ): Triple<String, String, String> =
        connection.query(
            "SELECT display_name, name_key, canonical_path FROM skills WHERE id = ?",
            { statement -> statement.setString(SqliteParameters.FIRST, skillId.value) },
        ) { result ->
            if (result.next()) {
                Triple(
                    result.getString("display_name"),
                    result.getString("name_key"),
                    result.getString("canonical_path"),
                )
            } else {
                null
            }
        } ?: error("skill missing for removal commit")

    fun insertTrashRecord(
        connection: Connection,
        input: TrashRecordInput,
    ) {
        connection.execute(
            """
            INSERT INTO trash_records (
                skill_id, operation_id, display_name, name_key,
                former_canonical_path, trash_path, agent_selections
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) { statement ->
            statement.setString(SqliteParameters.FIRST, input.skillId.value)
            statement.setString(SqliteParameters.SECOND, input.operationId)
            statement.setString(SqliteParameters.THIRD, input.skill.first)
            statement.setString(SqliteParameters.FOURTH, input.skill.second)
            statement.setString(SqliteParameters.FIFTH, input.skill.third)
            statement.setString(SqliteParameters.SIXTH, input.trashPath.toString())
            statement.setString(
                SqliteParameters.SEVENTH,
                input.formerAgents.joinToString(";") { it.wireValue },
            )
        }
    }

    fun loadInstallations(
        connection: Connection,
        skillId: SkillId,
    ): List<AgentInstallationSnapshot> {
        val installations = mutableListOf<AgentInstallationSnapshot>()
        connection
            .prepareStatement(
                "SELECT agent, destination, desired, observed FROM installations WHERE skill_id = ?",
            ).use { statement ->
                statement.setString(SqliteParameters.FIRST, skillId.value)
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

    fun parseAgents(serialized: String): Map<AgentId, Path> =
        if (serialized.isBlank()) {
            emptyMap()
        } else {
            serialized
                .split(";")
                .mapNotNull { entry ->
                    val parts = entry.split("=", limit = 2)
                    if (parts.size != 2) {
                        return@mapNotNull null
                    }
                    val agent = AgentId.fromWire(parts[0]) ?: return@mapNotNull null
                    agent to Path.of(decodePath(parts[1]))
                }.toMap()
        }

    fun encodePath(path: Path): String =
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
}

internal object FilesSupport {
    fun ensureParent(path: Path) {
        val parent = path.parent
        if (parent != null) {
            java.nio.file.Files
                .createDirectories(parent)
        }
    }
}

internal object SchemaMigrator {
    private const val FIRST_PARAMETER = 1
    private const val FIRST_SCHEMA_VERSION = 1
    private const val LEGACY_SCHEMA_VERSION_ONE = 1
    private const val LEGACY_SCHEMA_VERSION_TWO = 2
    private const val LEGACY_SCHEMA_VERSION_THREE = 3
    private const val SCHEMA_VERSION = 4

    fun migrate(connection: Connection) {
        createVersionTable(connection)
        val version = readVersion(connection)
        when {
            version != null && version !in FIRST_SCHEMA_VERSION..SCHEMA_VERSION -> {
                check(false) { "unsupported schema version $version" }
            }

            version == null -> {
                initializeCurrentSchema(connection)
            }

            else -> {
                migrateExistingSchema(connection, version)
            }
        }
    }

    private fun createVersionTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)
                """.trimIndent(),
            )
        }
    }

    private fun readVersion(connection: Connection): Int? =
        connection.prepareStatement("SELECT version FROM schema_version LIMIT 1").use { statement ->
            statement.executeQuery().use { result ->
                if (result.next()) result.getInt("version") else null
            }
        }

    private fun initializeCurrentSchema(connection: Connection) {
        SchemaTables.createCurrentSchema(connection)
        connection.prepareStatement("INSERT INTO schema_version (version) VALUES (?)").use {
            it.setInt(FIRST_PARAMETER, SCHEMA_VERSION)
            it.executeUpdate()
        }
    }

    private fun migrateExistingSchema(
        connection: Connection,
        version: Int,
    ) {
        SchemaTables.createCurrentSchema(connection)
        when (version) {
            LEGACY_SCHEMA_VERSION_ONE -> {
                connection.createStatement().use { statement ->
                    statement.execute("ALTER TABLE operations ADD COLUMN source_fingerprint TEXT")
                    statement.execute("ALTER TABLE operations ADD COLUMN staging_identity TEXT")
                    statement.execute("ALTER TABLE operations ADD COLUMN canonical_identity TEXT")
                    statement.execute("UPDATE schema_version SET version = 3")
                }
                migrateToVersion4(connection)
            }

            LEGACY_SCHEMA_VERSION_TWO -> {
                connection.createStatement().use { statement ->
                    statement.execute("ALTER TABLE operations ADD COLUMN staging_identity TEXT")
                    statement.execute("ALTER TABLE operations ADD COLUMN canonical_identity TEXT")
                    statement.execute("UPDATE schema_version SET version = 3")
                }
                migrateToVersion4(connection)
            }

            LEGACY_SCHEMA_VERSION_THREE -> {
                migrateToVersion4(connection)
            }

            SCHEMA_VERSION -> {
            }
        }
    }

    private fun migrateToVersion4(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("ALTER TABLE operations ADD COLUMN operation_kind TEXT NOT NULL DEFAULT 'Install'")
            statement.execute("ALTER TABLE operations ADD COLUMN management_snapshot TEXT")
            statement.execute("ALTER TABLE operations ADD COLUMN trash_path TEXT")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS trash_records (
                    skill_id TEXT PRIMARY KEY,
                    operation_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    name_key TEXT NOT NULL,
                    former_canonical_path TEXT NOT NULL,
                    trash_path TEXT NOT NULL,
                    agent_selections TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute("UPDATE schema_version SET version = $SCHEMA_VERSION")
        }
    }
}

private object SchemaTables {
    fun createCurrentSchema(connection: Connection) {
        createSkillsTable(connection)
        createInstallationsTable(connection)
        createOperationsTable(connection)
        createTrashTable(connection)
    }

    private fun createSkillsTable(connection: Connection) {
        connection.createStatement().use { statement ->
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
        }
    }

    private fun createInstallationsTable(connection: Connection) {
        connection.createStatement().use { statement ->
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
        }
    }

    private fun createOperationsTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS operations (
                    id TEXT PRIMARY KEY,
                    skill_id TEXT NOT NULL,
                    comparison_key TEXT NOT NULL,
                    operation_kind TEXT NOT NULL DEFAULT 'Install',
                    phase TEXT NOT NULL,
                    staging_path TEXT,
                    canonical_path TEXT,
                    staging_identity TEXT,
                    canonical_identity TEXT,
                    source_root TEXT,
                    source_fingerprint TEXT,
                    agent_destinations TEXT,
                    management_snapshot TEXT,
                    trash_path TEXT
                )
                """.trimIndent(),
            )
        }
    }

    private fun createTrashTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS trash_records (
                    skill_id TEXT PRIMARY KEY,
                    operation_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    name_key TEXT NOT NULL,
                    former_canonical_path TEXT NOT NULL,
                    trash_path TEXT NOT NULL,
                    agent_selections TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
}
