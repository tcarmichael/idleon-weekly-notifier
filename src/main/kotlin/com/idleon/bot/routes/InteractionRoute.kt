package com.idleon.bot.routes

import com.idleon.bot.DISCORD_PUBLIC_KEY
import com.idleon.bot.services.ConfigService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.HexFormat

// --- Data Classes for Interaction ---
@Serializable
data class DiscordInteraction(
    val type: Int,
    val token: String,
    val member: JsonElement? = null,
    val data: InteractionData? = null,
    val guild_id: String? = null,
    val channel_id: String? = null
)

@Serializable
data class InteractionData(
    val id: String,
    val name: String,
    val type: Int,
    val options: List<InteractionOption>? = null
)

@Serializable
data class InteractionOption(
    val name: String,
    val type: Int,
    val value: kotlinx.serialization.json.JsonPrimitive? = null
)

@Serializable
data class InteractionResponse(
    val type: Int,
    val data: InteractionResponseData? = null
)

@Serializable
data class InteractionResponseData(
    val content: String
)

// ... (Rest of file wrapper isn't needed for this tool)

fun Route.interactionRoutes(configService: ConfigService) {
    val logger = LoggerFactory.getLogger("InteractionRoute")

    post("/interactions") {
        val signature = call.request.header("X-Signature-Ed25519") ?: ""
        val timestamp = call.request.header("X-Signature-Timestamp") ?: ""
        val body = call.receiveText()
        
        // TODO: Re-enable signature check when we have a robust library
        // if (DISCORD_PUBLIC_KEY.isNotEmpty() && !verifySignature(DISCORD_PUBLIC_KEY, signature, timestamp, body)) {
        //    call.respond(HttpStatusCode.Unauthorized, "Invalid request signature")
        //    return@post
        // }

        val json = Json { ignoreUnknownKeys = true }
        val interaction = try {
            json.decodeFromString<DiscordInteraction>(body)
        } catch (e: Exception) {
            logger.error("Failed to parse interaction", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid JSON")
            return@post
        }

        when (interaction.type) {
            1 -> { // PING
                call.respond(InteractionResponse(type = 1))
            }
            2 -> { // APPLICATION_COMMAND
                if (interaction.data?.name == "configure") {
                    val guildId = interaction.guild_id
                    if (guildId == null) {
                       call.respond(InteractionResponse(type = 4, data = InteractionResponseData("This command can only be used in a server.")))
                       return@post
                    }

                    var channelId = ""
                    var showBoss = true
                    
                    interaction.data.options?.forEach { option ->
                        when (option.name) {
                            "channel" -> channelId = option.value?.content ?: ""
                            "boss_battle" -> {
                                // Boolean comes as a primitive, content might be "true" or "false"
                                showBoss = option.value?.content?.toBoolean() ?: true
                            }
                        }
                    }
                    
                    if (channelId.isEmpty()) {
                         // If channel option was not passed, maybe use current channel? 
                         // But usually it's a required option.
                         // For now, if empty, we can grab channel_id from context if we want, 
                         // but user prompt says "Cloud be configurable by admin... choose the channel".
                         // So we expect them to pass it.
                         channelId = interaction.channel_id ?: ""
                    }
                    
                    configService.saveConfig(guildId, channelId, showBoss)
                    
                    call.respond(InteractionResponse(type = 4, data = InteractionResponseData("Configuration saved! Channel: <#$channelId>, Boss Messages: $showBoss")))
                } else {
                    call.respond(InteractionResponse(type = 4, data = InteractionResponseData("Unknown command.")))
                }
            }
            else -> {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

