package dev.doyu.econet.paper

import kotlinx.coroutines.CoroutineDispatcher
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/** 장부는 쓰기 스레드에서 돌지만 월드·인벤토리·접속자 목록은 메인 스레드에서만 만질 수 있으므로, 돌아갈 길을 둔다. */
class MainThreadDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !plugin.server.isPrimaryThread

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        plugin.server.scheduler.runTask(plugin, block)
    }
}
