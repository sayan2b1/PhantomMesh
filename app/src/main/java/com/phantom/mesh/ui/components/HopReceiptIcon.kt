package com.phantom.mesh.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phantom.mesh.ui.theme.CheckBlue
import com.phantom.mesh.ui.theme.CheckGray

/**
 * Visualizes the 3-tier hop-state read receipt system:
 *
 * 1. "saved"     -> 1 gray check  ("Saved to Local DB")
 * 2. "mesh_sent" -> 1 blue check  ("Handed off to Mesh Router" / Cloud)
 * 3. "delivered" -> 2 blue checks ("Received by Target Peer" via bounced ACK)
 */
@Composable
fun HopReceiptIcon(
    deliveryStatus: String,
    modifier: Modifier = Modifier
) {
    when (deliveryStatus) {
        "saved" -> {
            // One Gray Check: Written to Room DB
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Saved to Local DB",
                tint = CheckGray,
                modifier = modifier.size(13.dp)
            )
        }
        "mesh_sent" -> {
            // One Blue Check: Handed off to Mesh Router or Cloud
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Handed off to Mesh Router",
                tint = CheckBlue,
                modifier = modifier.size(13.dp)
            )
        }
        "delivered", "read" -> {
            // Two Blue Checks: Received by Target Peer (Mesh ACK bounced back)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = CheckBlue,
                    modifier = Modifier.size(13.dp)
                )
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Received by Target Peer",
                    tint = CheckBlue,
                    modifier = Modifier
                        .size(13.dp)
                        .offset(x = (-6).dp)
                )
            }
        }
        else -> {
            // Default pending state
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Local Pending",
                tint = CheckGray.copy(alpha = 0.5f),
                modifier = modifier.size(13.dp)
            )
        }
    }
}
