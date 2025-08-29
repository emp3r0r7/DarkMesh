package com.geeksville.mesh.service

import android.content.Context
import com.geeksville.mesh.database.MeshtasticDatabase
import com.geeksville.mesh.database.entity.QuickChatAction
import kotlinx.coroutines.runBlocking

object QuickChatBridge {
    @JvmStatic
    fun getQuickChats(context: Context): List<QuickChatAction> {
        return runBlocking {
            val db = MeshtasticDatabase.getDatabase(context)
            db.quickChatActionDao().getAllOnce()
        }
    }
}
