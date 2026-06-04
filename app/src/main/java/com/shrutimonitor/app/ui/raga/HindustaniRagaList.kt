package com.shrutimonitor.app.ui.raga

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.shrutimonitor.app.ui.theme.SurfaceDark
import com.shrutimonitor.app.ui.theme.SurfaceLightDark
import com.shrutimonitor.app.ui.theme.TextPrimary
import com.shrutimonitor.app.ui.theme.TextSecondary

/**
 * List of Hindustani Ragas, grouped alphabetically with sticky section headers.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HindustaniRagaList(
    ragas: List<Raga>,
    activeRaga: Raga?,
    onRagaClick: (Raga) -> Unit,
    modifier: Modifier = Modifier
) {
    // Group and sort ragas alphabetically
    val groupedRagas = remember(ragas) {
        ragas.sortedBy { it.name }
            .groupBy { it.name.first().uppercaseChar() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        groupedRagas.forEach { (char, ragaList) ->
            // Sticky section header (A, B, C...)
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0F)) // Dark background matching screen
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = char.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimarySaffron
                    )
                }
            }

            items(ragaList) { raga ->
                val isActive = activeRaga?.name == raga.name
                
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) PrimarySaffron.copy(alpha = 0.08f) else SurfaceDark
                    ),
                    border = if (isActive) CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(PrimarySaffron)
                    ) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onRagaClick(raga) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = raga.name,
                                fontSize = 18.sp,
                                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (raga.thaat != null) {
                                Text(
                                    text = "Thaat: ${raga.thaat}",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Chromatic Swara Signature Bubble Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 0..11) {
                                val isSwaraInRaga = raga.activeSwaras.any { it.index == i }
                                val swara = Swara.fromIndex(i)
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSwaraInRaga) PrimarySaffron else SurfaceLightDark
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = swara.hindustaniAbbr,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSwaraInRaga) Color.Black else TextSecondary.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Aaroha Scale Pills
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Aaroha: ", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                raga.aaroha.forEach { sw ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceLightDark)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = sw.hindustaniAbbr, fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Avaroha Scale Pills
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Avaroha: ", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                raga.avaroha.forEach { sw ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceLightDark)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = sw.hindustaniAbbr, fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
