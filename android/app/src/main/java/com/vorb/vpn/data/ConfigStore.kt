package com.vorb.vpn.data

import android.content.Context
import java.io.File

data class ConfigSummary(
    val address: String? = null,
    val endpoint: String? = null,
    val dns: String? = null,
) {
    val isEmpty: Boolean
        get() = address == null && endpoint == null && dns == null
}

class ConfigStore(context: Context) {
    private val file = File(context.filesDir, "vorb.conf")

    fun load(): String? = runCatching {
        if (file.isFile) file.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    fun save(text: String) {
        file.writeText(text.trim().trimEnd() + "\n")
    }

    fun clear() {
        runCatching { file.delete() }
    }

    fun summarize(text: String): ConfigSummary {
        return ConfigSummary(
            address = findValue(text, "Address"),
            endpoint = findValue(text, "Endpoint"),
            dns = findValue(text, "DNS"),
        )
    }

    private fun findValue(text: String, key: String): String? {
        val regex = Regex("(?im)^\\s*${Regex.escape(key)}\\s*=\\s*(.+?)\\s*$")
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
