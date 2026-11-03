package dev.doyu.econet.economy

import java.util.UUID

class AccountManager(
    initialAccounts: List<Account> = emptyList(),
    private val onAccountChanged: (Account) -> Unit = {},
    private val onAccountsChanged: (List<Account>) -> Unit = { accounts ->
        accounts.forEach(onAccountChanged)
    },
    private val onAccountCreated: (Account) -> Unit = {}
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
            try {
                onAccountCreated(it)
            } catch (e: Exception) {
                accounts.remove(it.id)
                ownerIndex.remove(ownerId)
                throw e
            }
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

    fun transfer(from: Account, to: Account, amount: Money): Boolean {
        require(from.id != to.id) {
            "같은 계좌로 이체할 수 없습니다."
        }
        require(amount > Money.ZERO) {
            "이체액은 양수여야 함"
        }
        if (!from.canWithdraw(amount)) return false

        val previousFromBalance = from.balance
        val previousToBalance = to.balance

        from.withdraw(amount)
        to.deposit(amount)

        try {
            onAccountsChanged(listOf(from, to))
        } catch (e: Exception) {
            from.restoreBalance(previousFromBalance)
            to.restoreBalance(previousToBalance)
            throw e
        }

        return true
    }
}