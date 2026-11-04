package dev.doyu.econet.paper.command

import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.paper.format.MoneyFormat

/** 실패 사유가 늘어나면 컴파일러가 여기서 알려 주도록, 모든 명령어가 이 한 곳의 when을 거친다. */
fun failureMessage(failure: TransferResult.Failure, money: MoneyFormat): String = when (failure) {
    is TransferResult.Failure.InsufficientFunds ->
        "잔액이 부족합니다. 현재 ${money.display(failure.balance)}, 필요 ${money.display(failure.required)}"
    TransferResult.Failure.SameAccount -> "자기 자신에게는 보낼 수 없습니다."
    TransferResult.Failure.Overflow -> "금액이 너무 커서 처리할 수 없습니다."
    TransferResult.Failure.InvalidRoute -> "허용되지 않는 거래입니다."
    TransferResult.Failure.Locked -> "장부 점검 중이라 거래가 잠시 중단되었습니다. 관리자에게 문의하세요."
}
