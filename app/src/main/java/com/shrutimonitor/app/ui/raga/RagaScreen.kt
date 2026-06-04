package com.shrutimonitor.app.ui.raga

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shrutimonitor.app.data.Raga
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.ui.theme.SecondaryViolet
import com.shrutimonitor.app.ui.theme.SurfaceDark
import com.shrutimonitor.app.ui.theme.TextPrimary
import com.shrutimonitor.app.ui.theme.TextSecondary

enum class RagaCategory {
    HINDUSTANI,
    CARNATIC
}

/**
 * Root Composable for the Raga Library database.
 *
 * Allows users to search and activate Hindustani/Carnatic scale filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagaScreen(
    modifier: Modifier = Modifier,
    viewModel: RagaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf(RagaCategory.HINDUSTANI) }

    // Raga selected for confirmation sheet
    var ragaToActivate by remember { mutableStateOf<Raga?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Search bar input
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(text = "Search raga, thaat, or melakarta...", color = TextSecondary) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = TextSecondary,
                        modifier = Modifier.clickable { viewModel.setSearchQuery("") }
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = SecondaryViolet,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 12.dp)
        )

        // Custom Category tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RagaCategory.entries.forEach { cat ->
                val isSelected = selectedCategory == cat
                val label = if (cat == RagaCategory.HINDUSTANI) "Hindustani" else "Carnatic (Melakartas)"
                val selectColor = if (cat == RagaCategory.HINDUSTANI) PrimarySaffron else SecondaryViolet

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) selectColor else Color.Transparent
                        )
                        .clickable { selectedCategory = cat },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            if (cat == RagaCategory.HINDUSTANI) Color.Black else Color.White
                        } else {
                            TextSecondary
                        }
                    )
                }
            }
        }

        // Active filter status bar
        if (state.activeRaga != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = PrimarySaffron,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Active Filter: ${state.activeRaga!!.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimarySaffron
                    )
                }
                
                Text(
                    text = "Clear Filter",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clickable { viewModel.deactivateRaga() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Crossfade between lists based on category selection
        Crossfade(
            targetState = selectedCategory,
            label = "ragaCategoryCrossfade",
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { category ->
            when (category) {
                RagaCategory.HINDUSTANI -> {
                    HindustaniRagaList(
                        ragas = state.filteredHindustaniRagas,
                        activeRaga = state.activeRaga,
                        onRagaClick = { ragaToActivate = it }
                    )
                }
                RagaCategory.CARNATIC -> {
                    CarnaticRagaList(
                        melakartas = state.filteredCarnaticMelakartas,
                        activeRaga = state.activeRaga,
                        onRagaClick = { ragaToActivate = it }
                    )
                }
            }
        }

        // Confirmation bottom sheet
        ragaToActivate?.let { raga ->
            RagaActivationSheet(
                raga = raga,
                onConfirm = {
                    viewModel.activateRaga(raga)
                    ragaToActivate = null
                },
                onDismiss = { ragaToActivate = null }
            )
        }
    }
}
