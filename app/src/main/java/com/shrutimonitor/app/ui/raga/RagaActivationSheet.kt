package com.shrutimonitor.app.ui.raga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.data.Raga
import com.shrutimonitor.app.data.Swara
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.ui.theme.SecondaryViolet
import com.shrutimonitor.app.ui.theme.SurfaceDark
import com.shrutimonitor.app.ui.theme.SurfaceLightDark
import com.shrutimonitor.app.ui.theme.TextPrimary
import com.shrutimonitor.app.ui.theme.TextSecondary

/**
 * Confirmation sheet displayed when clicking a raga, prompting the user
 * to filter active swaras to only this scale profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagaActivationSheet(
    raga: Raga?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    if (raga == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Activate Raga Scale?",
                fontSize = 20.sp,
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = raga.name,
                fontSize = 24.sp,
                fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                color = if (raga.isHindustani) PrimarySaffron else SecondaryViolet
            )

            val parentText = if (raga.isHindustani) {
                raga.thaat?.let { "Hindustani Thaat: $it" } ?: "Hindustani"
            } else {
                raga.melaKartaNumber?.let { "Carnatic Melakarta #$it" } ?: "Carnatic"
            }
            Text(
                text = parentText,
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Swara scale preview
            Text(
                text = "Scale Swaras Profile:",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0..11) {
                    val isSwaraInRaga = raga.activeSwaras.any { it.index == i }
                    val swara = Swara.fromIndex(i)
                    val label = if (raga.isHindustani) swara.hindustaniAbbr else swara.carnaticAbbr
                    val activeBgColor = if (raga.isHindustani) PrimarySaffron else SecondaryViolet

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSwaraInRaga) activeBgColor else SurfaceLightDark
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSwaraInRaga) {
                                if (raga.isHindustani) Color.Black else Color.White
                            } else {
                                TextSecondary.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Actions buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Cancel", fontSize = 14.sp)
                }

                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (raga.isHindustani) PrimarySaffron else SecondaryViolet,
                        contentColor = if (raga.isHindustani) Color.Black else Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Activate Filter", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
