package dev.doyu.econet.core

import dev.doyu.econet.core.AccountId.CashPool
import dev.doyu.econet.core.AccountId.Government
import dev.doyu.econet.core.AccountId.Issuance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.time.Clock
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerInvariantTest {
    private val ledger = InMemoryLedger(Clock.systemUTC())
    private val system = Actor.System("test")
    private val players = List(20) { AccountId.Player(UUID.randomUUID()) }
    private val regularAccounts: List<AccountId> = players + Government
    private val accounts: List<AccountId> = regularAccounts + listOf(Issuance, CashPool)

    @Test
    fun `thousands of random transfers keep the books balanced`() = runBlocking {
        val random = Random(42)
        var successes = 0

        repeat(5_000) {
            val kind = TxKind.entries.random(random)
            val regular = regularAccounts.random(random)
            val (from, to) = when {
                // 일부는 일부러 아무 경로나 던져서 거절 경로도 섞는다.
                random.nextInt(10) == 0 -> accounts.random(random) to accounts.random(random)
                kind == TxKind.Mint -> Issuance to Government
                kind == TxKind.Burn -> Government to Issuance
                kind == TxKind.CashOut -> regular to CashPool
                kind == TxKind.CashIn -> CashPool to regular
                else -> regular to regularAccounts.random(random)
            }
            val result = ledger.transfer(from, to, Amount(random.nextLong(1, 1_000)), kind, system)
            when (result) {
                is TransferResult.Success -> successes++
                is TransferResult.Failure.InsufficientFunds,
                TransferResult.Failure.SameAccount,
                TransferResult.Failure.Overflow,
                TransferResult.Failure.InvalidRoute -> Unit
            }
        }

        // 잔액 부족·경로 위반으로 거절되는 거래가 많으므로, 실제로 돈이 돌았는지도 확인한다.
        assertTrue(successes > 500, "only $successes transfers succeeded")
        val report = ledger.verifyIntegrity()
        assertEquals(BigInteger.ZERO, report.totalBalance)
        assertEquals(emptyList(), report.mismatches)
    }

    @Test
    fun `concurrent transfers preserve the total`() = runBlocking {
        ledger.transfer(Issuance, Government, Amount(1_000_000), TxKind.Mint, system)
        for (player in players) {
            ledger.transfer(Government, player, Amount(10_000), TxKind.Reward, system)
        }

        withContext(Dispatchers.Default) {
            repeat(500) { worker ->
                launch {
                    val random = Random(worker)
                    repeat(50) {
                        ledger.transfer(players.random(random), players.random(random), Amount(random.nextLong(1, 500)), TxKind.Transfer, system)
                    }
                }
            }
        }

        val report = ledger.verifyIntegrity()
        assertTrue(report.isIntact, report.toString())
        assertEquals(Balance(-1_000_000), ledger.balanceOf(Issuance))
        assertTrue(players.all { ledger.balanceOf(it) >= Balance.ZERO })
    }

    @Test
    fun `tampered balances are reported`() = runBlocking {
        val tampered = object : Ledger by ledger {
            override suspend fun allBalances() = ledger.allBalances() + (players[0] to Balance(5))
        }
        ledger.transfer(Issuance, Government, Amount(10), TxKind.Mint, system)

        val report = tampered.verifyIntegrity()

        assertEquals(BigInteger.valueOf(5), report.totalBalance)
        assertEquals(listOf(BalanceMismatch(players[0], stored = Balance(5), replayed = Balance.ZERO)), report.mismatches)
    }
}
