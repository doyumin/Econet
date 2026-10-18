package dev.doyu.econet.economy

import java.util.UUID

class AccountManager(
    initialAccounts: List<Account> = emptyList(),
    private val onAccountChanged: (Account) -> Unit = {}
) {
    private val accounts = mutableMapOf<UUID, Account>()
    private val ownerIndex = mutableMapOf<UUID, UUID>()

    init {
        initialAccounts.forEach { account ->
            accounts[account.id] = account
            ownerIndex[account.ownerId] = account.id
        }
    }

    fun getAccount(accountId: UUID): Account? =
        accounts[accountId]

    fun getAccountByOwner(ownerId: UUID): Account =
        ownerIndex[ownerId]?.let { accounts[it] }
            ?: createAccount(ownerId)

    private fun createAccount(ownerId: UUID): Account =
        Account(
            id = UUID.randomUUID(),
            ownerId = ownerId
        ).also {
            accounts[it.id] = it
            ownerIndex[ownerId] = it.id
        }

    fun getAllAccounts(): List<Account> =
        accounts.values.toList()

    fun deposit(account: Account, amount: Money) {
        val previousBalance = account.balance

        account.deposit(amount)

        try {
            onAccountChanged(account)
        } catch (e: Exception) {
            account.restoreBalance(previousBalance)
            throw e
        }
    }

    fun withdraw(account: Account, amount: Money): Boolean {
        if (!account.canWithdraw(amount)) return false

        val previousBalance = account.balance

        if (!account.withdraw(amount)) return false

        try {
            onAccountChanged(account)
        } catch (e: Exception) {
            account.restoreBalance(previousBalance)
            throw e
        }

        return true
    }
}