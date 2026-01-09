package com.idleon.bot.services

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.SetOptions
import org.slf4j.LoggerFactory

data class GuildConfig(
    val guildId: String = "",
    val channelId: String = "",
    val showBossBattle: Boolean = true
)

class ConfigService(projectId: String) {
    private val logger = LoggerFactory.getLogger(ConfigService::class.java)
    private val db: Firestore = FirestoreOptions.getDefaultInstance().toBuilder()
        .setProjectId(projectId)
        .build()
        .service
    
    private val collectionName = "guild_configs"

    fun saveConfig(guildId: String, channelId: String, showBossBattle: Boolean) {
        val data = mapOf(
            "guildId" to guildId,
            "channelId" to channelId,
            "showBossBattle" to showBossBattle
        )
        // Using set with merge to update or create
        db.collection(collectionName).document(guildId).set(data, SetOptions.merge())
        logger.info("Saved config for guild $guildId")
    }

    fun getConfigs(): List<GuildConfig> {
        val query = db.collection(collectionName).get()
        val querySnapshot = query.get()
        
        return querySnapshot.documents.map { doc ->
            GuildConfig(
                guildId = doc.getString("guildId") ?: doc.id,
                channelId = doc.getString("channelId") ?: "",
                showBossBattle = doc.getBoolean("showBossBattle") ?: true
            )
        }
    }
}
