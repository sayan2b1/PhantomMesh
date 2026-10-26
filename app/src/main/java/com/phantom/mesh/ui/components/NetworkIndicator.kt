package com.phantom.mesh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.phantom.mesh.network.NetworkTransport
import com.phantom.mesh.ui.theme.TransportBluetooth
import com.phantom.mesh.ui.theme.TransportCloud
import com.phantom.mesh.ui.theme.TransportOffline
import com.phantom.mesh.ui.theme.TransportWifiDirect

/**
 * Dynamic network indicator in the top app bar indicating current active transport layer.
 * Displays animated icons:
 * - Cloud (Firebase) ☁
 * - Wi-Fi Direct (High-bandwidth mesh) 📶
 * - Bluetooth (Handshake/discovery) 🔵
 * - Offline (Nearby scanning) ⚠️
 */
@Composable
fun NetworkIndicator(
    transport: NetworkTransport,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (icon, color, label) = when (transport) {
        NetworkTransport.Cloud -> Triple(Icons.Default.Cloud, TransportCloud, "CLOUD")
        NetworkTransport.WifiDirect -> Triple(Icons.Default.Wifi, TransportWifiDirect, "WI-FI DIRECT")
        NetworkTransport.Bluetooth -> Triple(Icons.Default.Bluetooth, TransportBluetooth, "BLE MESH")
        NetworkTransport.Offline -> Triple(Icons.Default.WifiOff, TransportOffline, "SEEKING PEERS")
    }

    val animatedColor by animateColorAsState(targetValue = color, label = "color")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(animatedColor.copy(alpha = 0.12f))
            .border(1.dp, animatedColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pulsing status dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(if (transport == NetworkTransport.Offline) pulseAlpha else 1f)
                    .background(animatedColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animatedColor,
                modifier = Modifier.size(15.dp)
            )

            if (showLabel) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = animatedColor
                )
            }
        }
    }
}
