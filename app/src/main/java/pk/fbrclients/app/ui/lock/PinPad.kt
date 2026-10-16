package pk.fbrclients.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.fbrclients.app.security.PinHasher

/**
 * Shared layout for setting and entering the PIN:
 * title, message, PIN dots, keypad, and an optional extra button (fingerprint).
 */
@Composable
fun PinPadScreen(
    title: String,
    message: String?,
    isError: Boolean,
    pin: String,
    enabled: Boolean,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    extraButtonLabel: String? = null,
    onExtraButton: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("FBR Clients", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text = message ?: " ",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PinDots(length = pin.length)
            Spacer(Modifier.height(32.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(DEL, "0", OK),
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        PinKey(
                            label = key,
                            enabled = enabled && when (key) {
                                DEL -> pin.isNotEmpty()
                                OK -> PinHasher.isValidPin(pin)
                                else -> pin.length < PinHasher.MAX_LENGTH
                            },
                            onClick = {
                                when (key) {
                                    DEL -> onPinChange(pin.dropLast(1))
                                    OK -> onSubmit()
                                    else -> onPinChange(pin + key)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                if (extraButtonLabel != null) {
                    TextButton(onClick = onExtraButton, enabled = enabled) { Text(extraButtonLabel) }
                }
            }
        }
    }
}

private const val DEL = "⌫"
private const val OK = "OK"

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(72.dp),
    ) {
        Text(
            label,
            fontSize = if (label.length > 1) 18.sp else 26.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PinDots(length: Int) {
    val shown = maxOf(PinHasher.MIN_LENGTH, length)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(shown) { i ->
            val filled = i < length
            Box(
                Modifier
                    .size(16.dp)
                    .then(
                        if (filled) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    ),
            )
        }
    }
}
