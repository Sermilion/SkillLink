package skilllink.infrastructure.persistence

import skilllink.application.ports.InstallOperationStart
import skilllink.application.ports.ManagementOperationStart
import skilllink.application.ports.OperationJournalPort
import skilllink.application.ports.OperationKind
import skilllink.application.ports.OperationPaths
import skilllink.application.ports.OperationPhase
import skilllink.application.ports.OperationRecord
import skilllink.domain.library.SkillId
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

internal class SqliteOperationJournalDelegate(
    private val databasePath: Path,
) : OperationJournalPort {
    @Volatile
    private var schemaReady = false

    override fun beginInstall(start: InstallOperationStart) {
        openConnection().use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO operations (
                        id, skill_id, comparison_key, operation_kind, phase,
                        source_root, source_fingerprint, agent_destinations
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(SqliteParameters.FIRST, start.operationId)
                    statement.setString(SqliteParameters.SECOND, start.skillId.value)
                    statement.setString(SqliteParameters.THIRD, start.comparisonKey)
                    statement.setString(SqliteParameters.FOURTH, OperationKind.Install.name)
                    statement.setString(SqliteParameters.FIFTH, OperationPhase.Staging.name)
                    statement.setString(SqliteParameters.SIXTH, start.sourceRoot.toString())
                    statement.setString(SqliteParameters.SEVENTH, start.sourceFingerprint)
                    statement.setString(
                        SqliteParameters.EIGHTH,
                        start.agents.entries.joinToString(";") {
                            "${it.key.wireValue}=${SqliteCatalogHelpers.encodePath(it.value)}"
                        },
                    )
                    statement.executeUpdate()
                }
        }
    }

    override fun beginManagement(start: ManagementOperationStart) {
        openConnection().use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO operations (
                        id, skill_id, comparison_key, operation_kind, phase, canonical_path,
                        agent_destinations, management_snapshot
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(SqliteParameters.FIRST, start.operationId)
                    statement.setString(SqliteParameters.SECOND, start.skillId.value)
                    statement.setString(SqliteParameters.THIRD, start.comparisonKey)
                    statement.setString(SqliteParameters.FOURTH, start.kind.name)
                    statement.setString(SqliteParameters.FIFTH, OperationPhase.Linked.name)
                    statement.setString(SqliteParameters.SIXTH, start.canonicalPath.toString())
                    statement.setString(
                        SqliteParameters.SEVENTH,
                        start.agentDestinations.entries.joinToString(";") {
                            "${it.key.wireValue}=${SqliteCatalogHelpers.encodePath(it.value)}"
                        },
                    )
                    statement.setString(SqliteParameters.EIGHTH, start.managementSnapshot)
                    statement.executeUpdate()
                }
        }
    }

    override fun updatePhase(
        operationId: String,
        phase: OperationPhase,
        paths: OperationPaths,
    ) {
        openConnection().use { connection ->
            connection
                .prepareStatement(
                    """
                    UPDATE operations
                    SET phase = ?, staging_path = ?, canonical_path = ?,
                        staging_identity = ?, canonical_identity = ?, trash_path = ?
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(SqliteParameters.FIRST, phase.name)
                    statement.setString(SqliteParameters.SECOND, paths.stagingPath?.toString())
                    statement.setString(SqliteParameters.THIRD, paths.canonicalPath?.toString())
                    statement.setString(SqliteParameters.FOURTH, paths.stagingIdentity)
                    statement.setString(SqliteParameters.FIFTH, paths.canonicalIdentity)
                    statement.setString(SqliteParameters.SIXTH, paths.trashPath?.toString())
                    statement.setString(SqliteParameters.SEVENTH, operationId)
                    statement.executeUpdate()
                }
        }
    }

    override fun findIncomplete(): List<OperationRecord> =
        openConnection().use { connection ->
            val records = mutableListOf<OperationRecord>()
            connection.query(
                """
                SELECT id, skill_id, comparison_key, operation_kind, phase, staging_path, canonical_path,
                    staging_identity, canonical_identity, source_root, source_fingerprint,
                    agent_destinations, management_snapshot, trash_path
                FROM operations
                WHERE phase NOT IN (?, ?)
                """.trimIndent(),
                { statement ->
                    statement.setString(SqliteParameters.FIRST, OperationPhase.Completed.name)
                    statement.setString(SqliteParameters.SECOND, OperationPhase.RolledBack.name)
                },
            ) { result ->
                while (result.next()) {
                    records.add(
                        OperationRecord(
                            operationId = result.getString("id"),
                            skillId = SkillId(result.getString("skill_id")),
                            comparisonKey = result.getString("comparison_key"),
                            kind = operationKind(result.getString("operation_kind")),
                            phase = OperationPhase.valueOf(result.getString("phase")),
                            stagingPath = result.getString("staging_path")?.let(Path::of),
                            canonicalPath = result.getString("canonical_path")?.let(Path::of),
                            stagingIdentity = result.getString("staging_identity"),
                            canonicalIdentity = result.getString("canonical_identity"),
                            sourceRoot = result.getString("source_root")?.let(Path::of),
                            sourceFingerprint = result.getString("source_fingerprint"),
                            agentDestinations =
                                SqliteCatalogHelpers.parseAgents(
                                    result.getString("agent_destinations"),
                                ),
                            managementSnapshot = result.getString("management_snapshot"),
                            trashPath = result.getString("trash_path")?.let(Path::of),
                        ),
                    )
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

    private fun openConnection(): Connection {
        FilesSupport.ensureParent(databasePath)
        val connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")
        if (!schemaReady) {
            synchronized(this) {
                if (!schemaReady) {
                    try {
                        connection.autoCommit = false
                        SchemaMigrator.migrate(connection)
                        connection.commit()
                        schemaReady = true
                    } catch (error: IllegalStateException) {
                        connection.rollback()
                        connection.close()
                        throw error
                    } catch (error: SQLException) {
                        connection.rollback()
                        connection.close()
                        throw IllegalStateException("schema migration failed", error)
                    } finally {
                        if (!connection.isClosed) {
                            connection.autoCommit = true
                        }
                    }
                }
            }
        }
        return connection
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

private fun operationKind(value: String?): OperationKind = value?.let(OperationKind::valueOf) ?: OperationKind.Install
