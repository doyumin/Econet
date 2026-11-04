package dev.doyu.econet.paper

import dev.doyu.econet.paper.command.ADMIN_PERMISSION
import dev.doyu.econet.paper.command.BalanceCommand
import dev.doyu.econet.paper.command.EconetCommand
import dev.doyu.econet.paper.command.MintCommand
import dev.doyu.econet.paper.command.PayCommand
import dev.doyu.econet.paper.config.readSettings
import dev.doyu.econet.paper.format.MoneyFormat
import dev.doyu.econet.paper.integrity.IntegrityWatch
import dev.doyu.econet.paper.integrity.LockNotice
import dev.doyu.econet.paper.storage.SqliteLedger
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.bukkit.plugin.java.JavaPlugin
import java.time.Clock
import java.util.logging.Level

class EconetPlugin : JavaPlugin() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            logger.log(Level.SEVERE, "Unhandled error in Econet task", error)
        },
    )
    private var ledger: SqliteLedger? = null
    private var integrityLoop: Job? = null

    override fun onEnable() {
        saveDefaultConfig()
        val settings = readSettings(config)
        val money = MoneyFormat(settings.currency)

        dataFolder.mkdirs()
        val ledger = SqliteLedger(dataFolder.toPath().resolve("econet.db"), Clock.systemUTC())
        this.ledger = ledger

        // 기다리지 않고 메인 스레드에 넘기기만 한다. onDisable이 메인 스레드를 잡고 작업을 기다리는 동안 교착되지 않게.
        val integrity = IntegrityWatch(ledger, logger) { message ->
            server.scheduler.runTask(this) { _ ->
                server.onlinePlayers.filter { it.hasPermission(ADMIN_PERMISSION) }.forEach { it.sendMessage(message) }
            }
        }
        // 첫 거래가 일어나기 전에 장부를 믿어도 되는지 확인해야 하므로, 시작 시 검사만은 끝날 때까지 기다린다.
        runBlocking { integrity.check() }
        integrityLoop = integrity.runEvery(settings.integrityCheckInterval, scope)

        server.pluginManager.registerEvents(LockNotice(ledger), this)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            commands.register("balance", "잔액을 확인합니다.", listOf("잔액", "bal"), BalanceCommand(ledger, money, scope, server))
            commands.register("pay", "다른 플레이어에게 송금합니다.", listOf("송금"), PayCommand(ledger, money, scope, server))
            commands.register("mint", "정부 금고에 돈을 발행합니다.", listOf("발행"), MintCommand(ledger, money, scope, logger))
            commands.register("econet", "장부를 점검합니다.", listOf("경제"), EconetCommand(ledger, integrity, money, scope, server, logger))
        }
    }

    override fun onDisable() {
        integrityLoop?.cancel()
        // 진행 중인 거래가 커밋을 마치고 결과를 알릴 시간을 준다. 쓰기 자체는 쓰기 스레드에서 원자적으로 끝나므로 기다리지 못해도 장부는 안전하다.
        runBlocking { withTimeoutOrNull(SHUTDOWN_GRACE_MILLIS) { scope.coroutineContext.job.children.toList().joinAll() } }
        scope.cancel()
        ledger?.close()
    }

    private companion object {
        const val SHUTDOWN_GRACE_MILLIS = 5_000L
    }
}
