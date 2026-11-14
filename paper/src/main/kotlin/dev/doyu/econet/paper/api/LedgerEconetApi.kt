package dev.doyu.econet.paper.api

import dev.doyu.econet.api.ChargeResult
import dev.doyu.econet.api.EconetApi
import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import java.util.UUID

/** 외부 플러그인에는 Player ↔ Government 두 방향만 연다. 종류도 charge는 Purchase, reward는 Reward로 고정이다. */
class LedgerEconetApi(private val ledger: Ledger) : EconetApi {
    override suspend fun balanceOf(player: UUID): Long = ledger.balanceOf(AccountId.Player(player)).value

    override suspend fun charge(plugin: String, player: UUID, amount: Long, reason: String): ChargeResult =
        move(AccountId.Player(player), AccountId.Government, TxKind.Purchase, plugin, amount, reason)

    override suspend fun reward(plugin: String, player: UUID, amount: Long, reason: String): ChargeResult =
        move(AccountId.Government, AccountId.Player(player), TxKind.Reward, plugin, amount, reason)

    private suspend fun move(from: AccountId, to: AccountId, kind: TxKind, plugin: String, amount: Long, reason: String): ChargeResult {
        // 이름이 비면 장부에서 누가 움직였는지 알 수 없다. 호출한 쪽 코드의 실수이므로 값이 아니라 예외로 알린다.
        require(plugin.isNotBlank()) { "plugin name must not be blank" }
        if (amount <= 0) return ChargeResult.InvalidAmount
        return when (val result = ledger.transfer(from, to, Amount(amount), kind, Actor.System(plugin), reason)) {
            is TransferResult.Success -> ChargeResult.Success(result.transaction.id.value)
            is TransferResult.Failure.InsufficientFunds -> ChargeResult.InsufficientFunds(result.balance.value, result.required.value)
            TransferResult.Failure.Overflow -> ChargeResult.Overflow
            TransferResult.Failure.Locked -> ChargeResult.Locked
            TransferResult.Failure.SameAccount, TransferResult.Failure.InvalidRoute -> ChargeResult.Rejected
        }
    }
}
