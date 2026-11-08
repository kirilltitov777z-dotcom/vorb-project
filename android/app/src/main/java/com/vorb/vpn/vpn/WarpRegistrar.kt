package com.vorb.vpn.vpn

import com.wireguard.crypto.KeyPair
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Creates a fresh Cloudflare WARP registration and turns it into a
 * WireGuard configuration.
 *
 * This mirrors `wgcf register` + `wgcf generate`, but runs entirely on the
 * device: no backend, no account, no manual import. The private key never
 * leaves the phone.
 */
object WarpRegistrar {
    private const val REGISTER_URL = "https://api.cloudflareclient.com/v0a2158/reg"
    private const val CLIENT_VERSION = "a-6.11-2223"
    private const val TOS = "2021-09-14T00:00:00.000+00:00"
    private const val ENDPOINT = "engage.cloudflareclient.com:2408"

    suspend fun generate(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { register() }
    }

    private fun register(): String {
        val keyPair = KeyPair()
        val privateKey = keyPair.privateKey.toBase64()
        val publicKey = keyPair.publicKey.toBase64()

        val payload = JSONObject()
            .put("key", publicKey)
            .put("install_id", "")
            .put("fcm_token", "")
            .put("tos", TOS)
            .put("model", "PC")
            .put("locale", "en_US")
            .toString()

        val connection = (URL(REGISTER_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 25_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json; charset=UTF-8")
            setRequestProperty("CF-Client-Version", CLIENT_VERSION)
            setRequestProperty("User-Agent", "okhttp/3.12.1")
        }

        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            if (status !in 200..299) {
                throw IllegalStateException(
                    when (status) {
                        429 -> "Cloudflare временно ограничил регистрацию. Попробуйте позже."
                        in 500..599 -> "Сервер Cloudflare недоступен ($status). Попробуйте позже."
                        else -> "Cloudflare отклонил регистрацию ($status)."
                    }
                )
            }

            val result = JSONObject(body).optJSONObject("result") ?: JSONObject(body)
            val config = result.optJSONObject("config")
                ?: throw IllegalStateException("Cloudflare не вернул конфигурацию.")
            val peer = config.optJSONArray("peers")?.optJSONObject(0)
                ?: throw IllegalStateException("Cloudflare не вернул peer.")
            val peerKey = peer.optString("public_key").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Cloudflare не вернул публичный ключ peer.")

            val rawAddress = config.optJSONObject("interface")
                ?.optJSONObject("addresses")
                ?.optString("v4")
                ?.takeIf { it.isNotBlank() }
                ?: "172.16.0.2"
            val address = if (rawAddress.contains('/')) rawAddress else "$rawAddress/32"

            return buildString {
                appendLine("# Vorb • Cloudflare WARP")
                appendLine("# Telegram: https://t.me/VorbVpn")
                appendLine("# Храните файл в тайне: в нём приватный ключ.")
                appendLine()
                appendLine("[Interface]")
                appendLine("PrivateKey = $privateKey")
                appendLine("Address = $address")
                appendLine("DNS = 1.1.1.1")
                appendLine("MTU = 1280")
                appendLine()
                appendLine("[Peer]")
                appendLine("PublicKey = $peerKey")
                appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
                appendLine("Endpoint = $ENDPOINT")
            }.trimEnd() + "\n"
        } finally {
            connection.disconnect()
        }
    }
}
