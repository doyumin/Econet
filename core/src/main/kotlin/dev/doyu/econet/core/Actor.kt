package dev.doyu.econet.core

import java.util.UUID

/** 거래를 일으킨 주체. 돈이 오간 계좌와는 별개로 "누가 시켰는지"를 남긴다. */
sealed interface Actor {
    data class Player(val uuid: UUID) : Actor

    data class Admin(val uuid: UUID) : Actor

    data class System(val source: String) : Actor
}
