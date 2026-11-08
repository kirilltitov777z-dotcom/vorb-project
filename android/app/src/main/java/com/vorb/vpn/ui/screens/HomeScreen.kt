package com.vorb.vpn.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vorb.vpn.VorbUiState
import com.vorb.vpn.ui.VorbLinks
import java.util.Locale

@Composable
fun HomeScreen(
    state: VorbUiState,
    onToggleConnection: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeader(
            onWebsite = { uriHandler.openUri(VorbLinks.WEBSITE) },
            onTelegram = { uriHandler.openUri(VorbLinks.TELEGRAM) },
        )

        ConnectionCard(
            state = state,
            onToggleConnection = onToggleConnection,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ArrowDownward,
                label = "Получено",
                value = formatBytes(state.rxBytes),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ArrowUpward,
                label = "Отправлено",
                value = formatBytes(state.txBytes),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        ConfigCard(
            state = state,
            onOpenImport = onOpenImport,
        )

        HowItWorksCard(
            onWebsite = { uriHandler.openUri(VorbLinks.WEBSITE) },
            onTelegram = { uriHandler.openUri(VorbLinks.TELEGRAM) },
        )
    }
}

@Composable
private fun HomeHeader(onWebsite: () -> Unit, onTelegram: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "V",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Vorb",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
            )
            Text(
                text = "WireGuard VPN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onWebsite) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = "Сайт Vorb",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onTelegram) {
            Icon(
                imageVector = Icons.Rounded.Send,
                contentDescription = "Telegram Vorb",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    state: VorbUiState,
    onToggleConnection: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusTitle = when {
        state.busy && state.generating -> "Создаём конфиг…"
        state.busy -> "Соединение…"
        state.connected -> "Подключено"
        else -> "Отключено"
    }
    val statusSubtitle = when {
        state.busy && state.generating -> "Регистрируем устройство в Cloudflare WARP"
        state.busy -> "Применяем настройки WireGuard"
        state.connected -> "Трафик защищён туннелем WARP"
        state.config == null -> "Нажмите «Подключиться» — конфиг создастся сам"
        else -> "Готово к безопасному подключению"
    }
    val buttonLabel = when {
        state.busy -> "Подождите…"
        state.connected -> "Отключиться"
        else -> "Подключиться"
    }
    val buttonIcon = Icons.Rounded.PowerSettingsNew

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colorScheme.primaryContainer.copy(alpha = 0.72f),
                        colorScheme.surfaceVariant.copy(alpha = 0.48f),
                    )
                )
            )
            .padding(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(connected = state.connected, busy = state.busy)
                Text(
                    text = "CLOUDFLARE WARP",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(4.dp))

            ConnectionOrb(connected = state.connected, busy = state.busy)

            Text(
                text = statusTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onToggleConnection,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = if (state.connected) {
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(connected: Boolean, busy: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val tint = when {
        busy -> colorScheme.tertiary
        connected -> colorScheme.primary
        else -> colorScheme.outline
    }
    val label = when {
        busy -> "СОЕДИНЕНИЕ"
        connected -> "ЗАЩИЩЕНО"
        else -> "ВЫКЛЮЧЕНО"
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun ConnectionOrb(connected: Boolean, busy: Boolean) {
    val transition = rememberInfiniteTransition(label = "connection-orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    val accent = when {
        busy -> MaterialTheme.colorScheme.tertiary
        connected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier.size(188.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = size.minDimension / 2f
            drawCircle(color = accent.copy(alpha = 0.08f), radius = outerRadius)

            if (connected || busy) {
                drawCircle(
                    color = accent.copy(alpha = (1f - pulse) * 0.22f),
                    radius = outerRadius * (0.72f + pulse * 0.28f),
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(
                    color = accent.copy(alpha = (1f - pulse) * 0.10f),
                    radius = outerRadius * (0.55f + pulse * 0.45f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.96f),
                        accent.copy(alpha = 0.55f),
                    ),
                    center = center,
                    radius = outerRadius * 0.62f,
                ),
                radius = outerRadius * 0.58f,
            )
        }

        Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(52.dp),
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(11.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ConfigCard(
    state: VorbUiState,
    onOpenImport: () -> Unit,
) {
    val hasConfig = state.config != null
    val summary = state.summary

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (hasConfig) Icons.Rounded.CheckCircle else Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = if (hasConfig) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = if (hasConfig) "Конфиг готов" else "Конфиг создастся сам",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenImport) {
                    Text(if (hasConfig) "Заменить" else "Импорт")
                }
            }

            if (summary.isEmpty) {
                Text(
                    text = "При первом подключении Vorb сам зарегистрирует устройство в Cloudflare WARP. " +
                        "Можно также импортировать готовый .conf вручную.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                summary.address?.let { InfoLine(label = "Адрес", value = it) }
                summary.endpoint?.let { InfoLine(label = "Endpoint", value = it) }
                summary.dns?.let { InfoLine(label = "DNS", value = it) }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HowItWorksCard(onWebsite: () -> Unit, onTelegram: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Как это работает",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "1. Нажмите «Подключиться» — Vorb сам создаст конфиг WARP.\n" +
                    "2. Разрешите VPN, когда система спросит.\n" +
                    "3. Готово: трафик идёт через защищённый туннель.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            OutlinedButton(
                onClick = onWebsite,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Сайт Vorb")
            }
            OutlinedButton(
                onClick = onTelegram,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Telegram Vorb")
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 Б"
    if (bytes < 1024L) return "$bytes Б"

    val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
