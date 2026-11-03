package dev.doyu.econet.paper.storage

import dev.doyu.econet.economy.Account
import dev.doyu.econet.economy.Money
import java.io.File
import java.sql.DriverManager
import java.util.UUID

class SQLiteAccountStore(file: File) {

    private val url = "jdbc:sqlite:${file.absolutePath}"

    init {
        file.parentFile.mkdirs()

        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS accounts (
                        id TEXT PRIMARY KEY,
                        owner_id TEXT NOT NULL UNIQUE,
                        balance INTEGER NOT NULL CHECK (balance >= 0)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    fun load(): List<Account> =
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT id, owner_id, balance FROM accounts"
                ).use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                Account(
                                    id = UUID.fromString(result.getString("id")),
                                    ownerId = UUID.fromString(result.getString("owner_id")),
                                    initialBalance = Money(result.getLong("balance"))
                                )
                            )
                        }
                    }
                }
            }
        }

    fun save(account: Account) {
        save(listOf(account))
    }

    fun save(accounts: List<Account>) {
        DriverManager.getConnection(url).use { connection ->
            connection.autoCommit = false

            try {
                connection.prepareStatement(
                    """
                    INSERT INTO accounts (id, owner_id, balance)
                    VALUES (?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        balance = excluded.balance
                    """.trimIndent()
                ).use { statement ->
                    accounts.forEach { account ->
                        statement.setString(1, account.id.toString())
                        statement.setString(2, account.ownerId.toString())
                        statement.setLong(3, account.balance.amount)
                        statement.addBatch()
                    }

                    statement.executeBatch()
                }

                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
    }
}