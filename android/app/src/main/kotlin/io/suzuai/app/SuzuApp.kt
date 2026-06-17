package io.suzuai.app

import android.app.Application
import io.suzuai.app.data.ChatRepository
import io.suzuai.app.data.SettingsStore
import io.suzuai.app.data.SuzuClient
import io.suzuai.app.data.db.ChatDb

/**
 * Manual DI container — no Hilt to keep the APK tiny.
 *
 * Everything that needs to live across configuration changes (DB, settings,
 * HTTP client, repository) is exposed here as a lazy singleton.
 */
class SuzuApp : Application() {
    val settings by lazy { SettingsStore(this) }
    val database by lazy { ChatDb.build(this) }
    val client by lazy { SuzuClient(settings) }
    val repository by lazy { ChatRepository(database.chats(), database.messages()) }

    companion object {
        lateinit var instance: SuzuApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
