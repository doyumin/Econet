package dev.doyu.econet.api

import java.util.UUID

/**
 * 다른 플러그인이 Econet의 돈을 쓰기 위한 좁은 창구. Bukkit `ServicesManager`에서 꺼낸다.
 *
 * 발행이나 특수 계좌(현금 등) 경로는 일부러 열지 않는다. 바깥에서 쓸 수 있으면 "합 = 0, 정해진 경로로만"이 깨진다.
 * 타입은 Long·UUID만 쓴다. 금액은 정수 단위(듄)다.
 */
interface EconetApi {
    suspend fun balanceOf(player: UUID): Long

    /**
     * 플레이어 예금에서 정부 금고로 받는다(구역 보호비, 텔레포트 비용 등).
     *
     * @param plugin 호출한 플러그인 이름. 장부에 주체로 남는다.
     * @param reason 왜 받는지. 장부 메모로 남는다.
     */
    suspend fun charge(plugin: String, player: UUID, amount: Long, reason: String): ChargeResult

    /** 정부 금고에서 플레이어에게 준다. 금고가 비면 [ChargeResult.InsufficientFunds]다. 인자는 [charge]와 같다. */
    suspend fun reward(plugin: String, player: UUID, amount: Long, reason: String): ChargeResult
}
