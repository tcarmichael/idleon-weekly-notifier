package com.idleon.bot

import com.idleon.bot.services.ConfigService
import com.idleon.bot.services.SheetService
import com.idleon.bot.routes.interactionRoutes
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.createGlobalApplicationCommands
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.interaction.boolean
import dev.kord.rest.builder.interaction.string
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.HexFormat

// Simple environment config
val PROJECT_ID = System.getenv("PROJECT_ID") ?: "idleon-weekly-notifier"
val SHEETS_API_KEY = System.getenv("SHEETS_API_KEY")
val DISCORD_BOT_TOKEN = System.getenv("DISCORD_BOT_TOKEN") ?: ""
val DISCORD_PUBLIC_KEY = System.getenv("DISCORD_PUBLIC_KEY") ?: ""

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")
    val sheetService = SheetService(SHEETS_API_KEY)
    val configService = ConfigService(PROJECT_ID)
    
    // Initialize Kord client. We don't need to login to Gateway if we are just sending messages via REST
    // However, Kord usually requires a login for the 'kord' object to be useful for REST.
    // For purely serverless interaction response, we handle that via raw JSON.
    // But for the Cron job, we need to send proactive messages, which needs the Bot Token.
    val kordClient = if (DISCORD_BOT_TOKEN.isNotEmpty()) {
         runBlocking { 
             val kord = Kord(DISCORD_BOT_TOKEN)
             
             // Register Slash Command
             kord.createGlobalApplicationCommands {
                 input("configure", "Configure the bot for this server") {
                     string("channel", "The channel to send notifications to") {
                         required = true
                     }
                     boolean("boss_battle", "Enable weekly boss battle notifications") {
                         required = false
                     }
                 }
             }
             
             kord
         }
    } else {
        null
    }

    routing {
        get("/") {
            call.respondText("Idleon Weekly Notifier is running.")
        }

        // 1. Interaction Endpoint
        interactionRoutes(configService)
        
        // 2. Scheduled Cron Job Endpoint
        post("/cron") {
            logger.info("Cron job triggered")
            
            // Security: Cloud Scheduler sends OIDC token. Validation is recommended for prod.
            // For now, we assume this is private and internal only.
            
            try {
                // Fetch data
                val weeklyBossText = sheetService.getWeeklyBossText()
                val baseMessage = "W4 Lab Chip and Jewel shop is refreshing + W2 Weekly Boss is resetting."
                
                // Get all configs
                val configs = configService.getConfigs()
                
                var successCount = 0
                
                if (kordClient != null) {
                    configs.forEach { config ->
                        if (config.channelId.isNotEmpty()) {
                            try {
                                val channelId = Snowflake(config.channelId)
                                val finalMessage = StringBuilder(baseMessage)
                                if (config.showBossBattle) {
                                    finalMessage.append("\n\n**Weekly Boss Battle:**\n$weeklyBossText")
                                }
                                
                                // We need to use the REST API to send the message
                                kordClient.rest.channel.createMessage(channelId) {
                                    content = finalMessage.toString()
                                }
                                successCount++
                            } catch (e: Exception) {
                                logger.error("Failed to send message to guild ${config.guildId}", e)
                            }
                        }
                    }
                } else {
                    logger.warn("Discord Bot Token not configured, skipping message sending.")
                }
                
                call.respondText("Notifications sent to $successCount channels.")
            } catch (e: Exception) {
                logger.error("Error in cron job", e)
                call.respond(HttpStatusCode.InternalServerError, "Error executing cron job")
            }
        }
        
        // Configuration Endpoint (Alternative to Slash Command if interactions are hard)
        // We can just use a simple GET/POST here for manual configuration if needed
        // But the requirement is "configurable by a server admin".
        // Slash commands are best. I'll need to strictly implement the signature auth for /interactions.
    }
}
