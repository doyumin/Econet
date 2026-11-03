package dev.doyu.econet.economy

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountManagerTest {

    @Test
    fun `transfer changes both accounts and persists them together`() {
        val from = Account(UUID.randomUUID(), UUID.randomUUID(), 100L.coins)
        val to = Account(UUID.randomUUID(), UUID.randomUUID(), 20L.coins)
        var changedAccounts: List<Account>? = null
        val manager = AccountManager(
            initialAccounts = listOf(from, to),
            onAccountsChanged = { changedAccounts = it }
        )

        assertTrue(manager.transfer(from, to, 30L.coins))

        assertEquals(70L.coins, from.balance)
        assertEquals(50L.coins, to.balance)
        assertEquals(listOf(from, to), changedAccounts)
    }

    @Test
    fun `transfer leaves accounts unchanged when sender has insufficient funds`() {
        val from = Account(UUID.randomUUID(), UUID.randomUUID(), 10L.coins)
        val to = Account(UUID.randomUUID(), UUID.randomUUID(), 20L.coins)
        var persistenceCalls = 0
        val manager = AccountManager(
            initialAccounts = listOf(from, to),
            onAccountsChanged = { persistenceCalls++ }
        )

        assertFalse(manager.transfer(from, to, 30L.coins))

        assertEquals(10L.coins, from.balance)
        assertEquals(20L.coins, to.balance)
        assertEquals(0, persistenceCalls)
    }

    @Test
    fun `transfer restores both accounts when persistence fails`() {
        val from = Account(UUID.randomUUID(), UUID.randomUUID(), 100L.coins)
        val to = Account(UUID.randomUUID(), UUID.randomUUID(), 20L.coins)
        val manager = AccountManager(
            initialAccounts = listOf(from, to),
            onAccountsChanged = { error("persistence failed") }
        )

        assertFailsWith<IllegalStateException> {
            manager.transfer(from, to, 30L.coins)
        }

        assertEquals(100L.coins, from.balance)
        assertEquals(20L.coins, to.balance)
    }

    @Test
    fun `new account is persisted immediately`() {
        val ownerId = UUID.randomUUID()
        var createdAccount: Account? = null
        val manager = AccountManager(
            onAccountCreated = { createdAccount = it }
        )

        val account = manager.getAccountByOwner(ownerId)

        assertEquals(account, createdAccount)
        assertEquals(ownerId, account.ownerId)
        assertEquals(Money.ZERO, account.balance)
    }

    @Test
    fun `failed account persistence removes the new account`() {
        val ownerId = UUID.randomUUID()
        val manager = AccountManager(
            onAccountCreated = { error("persistence failed") }
        )

        assertFailsWith<IllegalStateException> {
            manager.getAccountByOwner(ownerId)
        }
        assertEquals(emptyList(), manager.getAllAccounts())
    }
}
