package dev.doyu.econet.paper.storage

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import java.util.UUID

// DB에 남는 문자열이라 클래스 이름이 바뀌어도 깨지지 않도록 직렬화 형식을 직접 정한다.

internal fun AccountId.toKey(): String = when (this) {
    is AccountId.Player -> "player:$uuid"
    AccountId.Government -> "government"
    AccountId.Issuance -> "issuance"
    AccountId.CashPool -> "cashpool"
}

internal fun accountIdOf(key: String): AccountId = when {
    key.startsWith("player:") -> AccountId.Player(UUID.fromString(key.removePrefix("player:")))
    key == "government" -> AccountId.Government
    key == "issuance" -> AccountId.Issuance
    key == "cashpool" -> AccountId.CashPool
    else -> error("Unknown account key: $key")
}

internal fun Actor.toKey(): String = when (this) {
    is Actor.Player -> "player:$uuid"
    is Actor.Admin -> "admin:$uuid"
    is Actor.System -> "system:$source"
}

internal fun actorOf(key: String): Actor = when {
    key.startsWith("player:") -> Actor.Player(UUID.fromString(key.removePrefix("player:")))
    key.startsWith("admin:") -> Actor.Admin(UUID.fromString(key.removePrefix("admin:")))
    key.startsWith("system:") -> Actor.System(key.removePrefix("system:"))
    else -> error("Unknown actor key: $key")
}
