package com.idleon.bot.services

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import java.io.IOException

class SheetService(private val apiKey: String?) {
    private val logger = LoggerFactory.getLogger(SheetService::class.java)
    private val spreadSheetId = "1z1P2ouvYhe2pryWoF0kIQE7QichYpJt1GaPPos-e-aw"
    private val range = "Discord!B2"

    private val service: Sheets = run {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        
        val requestInitializer = if (apiKey != null) {
            // If API Key is provided, we use it for public access (custom initializer)
            // But the Java library structure favors Credentials.
            // For simple API key usage, we might often just set it on the request.
            // However, the builder pattern here usually expects credentials.
            // Let's try to use Application Default Credentials if API key is not the primary mode,
            // or just use the API Key on the request query parameters if we were using raw HTTP.
            // With the client lib, it's often cleaner to use Credentials if possible, 
            // but for public sheets, an API Key is standard.
            // We'll return null here and set key on requests or allow ADC.
            null
        } else {
             // Fallback to ADC
             HttpCredentialsAdapter(GoogleCredentials.getApplicationDefault().createScoped(listOf(SheetsScopes.SPREADSHEETS_READONLY)))
        }

        Sheets.Builder(transport, jsonFactory, requestInitializer)
            .setApplicationName("Idleon Weekly Notifier")
            .build()
    }

    fun getWeeklyBossText(): String {
        return try {
            val request = service.spreadsheets().values().get(spreadSheetId, range)
            if (apiKey != null) {
                request.key = apiKey
            }
            
            val response = request.execute()
            val values = response.getValues()
            
            if (values.isNullOrEmpty() || values[0].isEmpty()) {
                logger.warn("No data found in range $range")
                return "Could not fetch weekly boss data."
            }
            
            val cellValue = values[0][0].toString()
            logger.info("Fetched weekly boss text: $cellValue")
            cellValue
        } catch (e: Exception) {
            logger.error("Error fetching data from sheets", e)
            throw e
        }
    }
}
