package com.example.cuidaleapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch

/**
 * Holds the horizontal shake offset for a list. Apply it via
 * Modifier.boundaryShake(listState).
 */
@Composable
fun rememberBoundaryShake(): Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }

/**
 * Plays a quick, subtle horizontal shake sequence.
 */
suspend fun Animatable<Float, AnimationVector1D>.shakeContainer() {
    val sequence = listOf(-8f, 7f, -6f, 5f, -4f, 3f, -2f, 1f, 0f)
    sequence.forEachIndexed { index, target ->
        animateTo(target, tween(if (index < 4) 60 else 80))
    }
}

/**
 * Fires a horizontal shake when the user pushes past the top or bottom
 * boundary of [listState] (overscroll) while dragging.
 */
@Composable
fun Modifier.boundaryShake(listState: LazyListState): Modifier {
    val shake = rememberBoundaryShake()
    val scope = rememberCoroutineScope()
    var fired by remember { mutableStateOf(false) }

    val connection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 0
                    val info = listState.layoutInfo
                    val lastIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    val atBottom = info.totalItemsCount > 0 && lastIndex == info.totalItemsCount - 1
                    val shouldShake = (atTop && available.y > 0) || (atBottom && available.y < 0)
                    if (shouldShake && !fired) {
                        fired = true
                        scope.launch { shake.shakeContainer() }
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress) fired = false
        }
    }

    return this.nestedScroll(connection).graphicsLayer {
        translationX = shake.value
        rotationZ = -shake.value * 0.09f
    }
}
