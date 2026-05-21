/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */




package moe.koiverse.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BigSeekBar(
    progressProvider: () -> Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.13f),
    color: Color = MaterialTheme.colorScheme.primary,
    steps: Int = 19,
) {
    val progress = progressProvider()
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val visualProgress = remember { Animatable(progress) }

    LaunchedEffect(progress, isDragged) {
        if (isDragged) {
            visualProgress.snapTo(progress)
        } else {
            val diff = abs(progress - visualProgress.value)
            if (diff > 0.02f) {
                visualProgress.animateTo(progress, animationSpec = tween(250))
            } else {
                visualProgress.snapTo(progress)
            }
        }
    }

    Slider(
        value = if (isDragged) progress else visualProgress.value,
        onValueChange = onProgressChange,
        valueRange = 0f..1f,
        steps = steps,
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(
            activeTrackColor = color,
            activeTickColor = color,
            thumbColor = color,
            inactiveTrackColor = background
        ),
        thumb = { 
            Spacer(modifier = Modifier.size(0.dp)) 
        },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = SliderDefaults.colors(
                    activeTrackColor = color,
                    activeTickColor = color,
                    inactiveTrackColor = background
                ),
                trackHeight = 10.dp
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    )
}
