package com.shrutimonitor.app.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.audio.RiyazSessionSummary
import com.shrutimonitor.app.audio.SavedSessionInfo
import com.shrutimonitor.app.data.Nomenclature
import com.shrutimonitor.app.data.Swara
import com.shrutimonitor.app.ui.theme.InTuneMint
import com.shrutimonitor.app.ui.theme.OutOfTuneCoral
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.util.HapticManager
import java.util.Locale

@Composable
fun RiyazStudioSheet(
    summary: RiyazSessionSummary,
    nomenclature: Nomenclature,
    savedSessions: List<SavedSessionInfo>,
    activeGhostSessionId: String?,
    playingSessionId: String?,
    onResetScorecard: () -> Unit,
    onToggleGhostTrace: (SavedSessionInfo) -> Unit,
    onTogglePlaySession: (SavedSessionInfo) -> Unit,
    onDeleteSession: (SavedSessionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = {
                    HapticManager.tick(view)
                    selectedTab = 0
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Intonation Scorecard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = {
                    HapticManager.tick(view)
                    selectedTab = 1
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Ghost Trace & Sessions (${savedSessions.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // ── TAB 0: INTONATION SCORECARD ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = String.format(Locale.US, "Overall Shruti Accuracy: %.1f%%", summary.overallAccuracyPercent),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.overallAccuracyPercent >= 85f) InTuneMint else PrimarySaffron
                    )
                    Text(
                        text = "Voiced frames: ${summary.totalVoicedFrames}  •  Varjya slips: ${summary.varjyaSlipCount}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        HapticManager.tick(view)
                        onResetScorecard()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (summary.swaraStats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sing or play a few notes to populate your per-Swara intonation histogram.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    items(summary.swaraStats, key = { it.swaraIndex }) { stat ->
                        val swara = Swara.fromIndex(stat.swaraIndex)
                        val label = if (nomenclature == Nomenclature.HINDUSTANI) swara.hindustaniAbbr else swara.carnaticAbbr
                        val barColor = when {
                            stat.accuracyPercent >= 85f -> InTuneMint
                            stat.accuracyPercent >= 70f -> PrimarySaffron
                            else -> OutOfTuneCoral
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(34.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((stat.accuracyPercent / 100f).coerceIn(0.04f, 1f))
                                        .fillMaxHeight()
                                        .background(barColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = String.format(Locale.US, "%.0f%% (%+.0f¢)", stat.accuracyPercent, stat.meanDeviationCents),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = barColor,
                                modifier = Modifier.width(76.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // ── TAB 1: GHOST TRACE & RECORDED SESSIONS ──
            Text(
                text = "Pin any recorded session as a translucent Ghost Trace on the Pitch Graph to practice matching its exact Gamaka contour.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (savedSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recorded sessions yet. Tap the Record button in the bottom drawer to capture a take.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    items(savedSessions, key = { it.id }) { session ->
                        val isGhostActive = session.id == activeGhostSessionId
                        val isPlaying = session.id == playingSessionId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isGhostActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isGhostActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.displayTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Sa: %.1f Hz  •  %ds  •  %d pts",
                                        session.saFrequency,
                                        (session.durationMs / 1000L).coerceAtLeast(1L),
                                        session.pointCount
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Pin/Unpin Ghost Trace Button
                                IconButton(
                                    onClick = {
                                        HapticManager.tick(view)
                                        onToggleGhostTrace(session)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timeline,
                                        contentDescription = "Pin Ghost Trace",
                                        tint = if (isGhostActive) InTuneMint else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Play/Stop Audio Button
                                IconButton(
                                    onClick = {
                                        HapticManager.tick(view)
                                        onTogglePlaySession(session)
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "Play Session Audio",
                                        tint = if (isPlaying) OutOfTuneCoral else MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Delete Session Button
                                IconButton(
                                    onClick = {
                                        HapticManager.heavyClick(view)
                                        onDeleteSession(session)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Session",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
