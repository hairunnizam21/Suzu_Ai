package io.suzuai.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ChatDb : RoomDatabase() {
    abstract fun chats(): ChatDao
    abstract fun messages(): MessageDao

    companion object {
        fun build(context: Context): ChatDb =
            Room.databaseBuilder(context, ChatDb::class.java, "suzu_chats.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
