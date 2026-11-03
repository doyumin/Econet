package dev.doyu.econet.paper

import dev.doyu.econet.economy.AccountManager
import dev.doyu.econet.paper.command.EcoCommand
import dev.doyu.econet.paper.command.MoneyCommand
import dev.doyu.econet.paper.command.PayCommand
import dev.doyu.econet.paper.storage.SQLiteAccountStore
import org.bukkit.plugin.java.JavaPlugin

class EconetPlugin : JavaPlugin() {

    lateinit var accountManager: AccountManager
        private set

    private lateinit var accountStore: SQLiteAccountStore

    override fun onEnable() {
        accountStore = SQLiteAccountStore(dataFolder.resolve("econet.db"))

        accountManager = AccountManager(
            initialAccounts = accountStore.load(),
            onAccountChanged = accountStore::save,
            onAccountsChanged = accountStore::save,
            onAccountCreated = accountStore::save
        )

        getCommand("money")?.setExecutor(MoneyCommand(accountManager))
        getCommand("eco")?.apply {
            val executor = EcoCommand(accountManager)
            setExecutor(executor)
            tabCompleter = executor
        }
        getCommand("pay")?.apply {
            val executor = PayCommand(accountManager)
            setExecutor(executor)
            tabCompleter = executor
        }
    }

    override fun onDisable() {
        if (::accountStore.isInitialized && ::accountManager.isInitialized) {
            accountStore.save(accountManager.getAllAccounts())
        }
    }

}