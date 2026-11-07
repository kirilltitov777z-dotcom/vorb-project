package com.vorb.vpn.vpn

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small wrapper around the official WireGuard tunnel library.
 *
 * GoBackend keeps the VpnService alive while the tunnel is up, so this
 * manager can be re-created (for example after an Activity recreation)
 * without tearing the tunnel down.
 */
class WireGuardManager(context: Context) {
    private val appContext = context.applicationContext
    private val backend: Backend by lazy { GoBackend(appContext) }
    private var tunnel: VorbTunnel? = null

    suspend fun connect(configText: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val config = Config.parse(BufferedReader(StringReader(configText)))
            val activeTunnel = tunnel ?: VorbTunnel().also { tunnel = it }
            backend.setState(activeTunnel, Tunnel.State.UP, config)
            check(backend.getState(activeTunnel) == Tunnel.State.UP) {
                "WireGuard не поднял туннель"
            }
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val activeTunnel = tunnel ?: return@runCatching
            backend.setState(activeTunnel, Tunnel.State.DOWN, null)
        }
    }

    suspend fun statistics(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        runCatching {
            val activeTunnel = tunnel ?: return@runCatching null
            val stats = backend.getStatistics(activeTunnel)
            stats.totalRx() to stats.totalTx()
        }.getOrNull()
    }

    fun isConnected(): Boolean = runCatching {
        val activeTunnel = tunnel ?: return@runCatching false
        backend.getState(activeTunnel) == Tunnel.State.UP
    }.getOrDefault(false)
}

private class VorbTunnel : Tunnel {
    @Volatile
    private var state: Tunnel.State = Tunnel.State.DOWN

    override fun getName(): String = "vorb"

    override fun onStateChange(newState: Tunnel.State) {
        state = newState
    }
}
