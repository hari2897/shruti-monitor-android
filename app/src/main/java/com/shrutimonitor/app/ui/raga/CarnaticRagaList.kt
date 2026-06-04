package com.shrutimonitor.app.ui.raga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrutimonitor.app.data.MelaKarta
import com.shrutimonitor.app.data.Raga
import com.shrutimonitor.app.data.Swara
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.ui.theme.SecondaryViolet
import com.shrutimonitor.app.ui.theme.SurfaceDark
import com.shrutimonitor.app.ui.theme.SurfaceLightDark
import com.shrutimonitor.app.ui.theme.TextPrimary
import com.shrutimonitor.app.ui.theme.TextSecondary

/**
 * List of Carnatic Melakartas (1-72) and their nested Janya Ragas.
 */
@Composable
fun CarnaticRagaList(
    melakartas: List<MelaKarta>,
    activeRaga: Raga?,
    onRagaClick: (Raga) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep track of which Melakarta's Janya list is expanded
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(melakartas, key = { it.number }) { melakarta ->
            val melakartaRaga = melakarta.toRaga()
            val isActive = activeRaga?.melaKartaNumber == melakarta.number && activeRaga.isHindustani == false && activeRaga.name == melakarta.name
            
            val isExpanded = expandedStates[melakarta.number] ?: false
            val hasJanyas = melakarta.janyaRagas.isNotEmpty()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) SecondaryViolet.copy(alpha = 0.08f) else SurfaceDark
                ),
                border = if (isActive) CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SecondaryViolet)
                ) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Melakarta Header Row (clicking selects the parent melakarta raga)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRagaClick(melakartaRaga) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(SecondaryViolet),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = melakarta.number.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = melakarta.name,
                                    fontSize = 18.sp,
                                    fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Chakra: ${melakarta.chakra} (${if (melakarta.isPratiMadhyama) "Prati Ma" else "Shuddha Ma"})",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        // Janya Dropdown Icon Trigger
                        if (hasJanyas) {
                            IconButton(
                                onClick = {
                                    expandedStates[melakarta.number] = !isExpanded
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Show Janya Ragas",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }

                    // Chromatic Swara Signature Bubble Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in 0..11) {
                            val isSwaraInRaga = melakarta.swaras.any { it.index == i }
                            val swara = Swara.fromIndex(i)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSwaraInRaga) SecondaryViolet else SurfaceLightDark
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = swara.carnaticAbbr,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSwaraInRaga) Color.White else TextSecondary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // Collapsible Nested Janya Ragas List
                    AnimatedVisibility(
                        visible = isExpanded && hasJanyas,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Janya Ragas (Derived Scales):",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )

                            melakarta.janyaRagas.forEach { janya ->
                                val isJanyaActive = activeRaga?.name == janya.name && activeRaga.isHindustani == false
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRagaClick(janya) }
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = if (isJanyaActive) PrimarySaffron else TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = janya.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isJanyaActive) PrimarySaffron else TextPrimary
                                        )
                                    }

                                    // Display its short aaroha notes sequence
                                    Text(
                                        text = janya.aaroha.joinToString(" ") { it.carnaticAbbr },
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
