package skilllink.infrastructure.persistence

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

internal class SqliteConnectionProvider(
  private val databasePath: Path,
) {
  @Volatile
  private var schemaReady = false

  fun openConnection(): Connection {
    FilesSupport.ensureParent(databasePath)
    val connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")
    if (!schemaReady) {
      synchronized(this) {
        if (!schemaReady) {
          migrate(connection)
        }
      }
    }
    return connection
  }

  private fun migrate(connection: Connection) {
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
