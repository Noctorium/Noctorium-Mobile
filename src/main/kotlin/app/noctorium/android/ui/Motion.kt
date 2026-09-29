package app.noctorium.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Whether things move. Provided once at the root from the listener's Animations setting; everything below
 * asks here rather than reading the setting, so switching it off stops every animation at once.
 *
 * The desktop has the same file, word for word but for the package, so both players move alike.
 */
val LocalMotion = staticCompositionLocalOf { true }

/**
 * How long things take, shared by every animation so the application moves at one pace.
 *
 * Short on purpose. A music player is used in passing, between other things, and motion that makes somebody
 * wait for a screen they already asked for is motion in the way.
 */
object MotionTiming {
    /** A press answering: an icon swapping, a heart filling. */
    const val QUICK = 150

    /** A screen or a page arriving. */
    const val STANDARD = 260

    /** What leaves goes quicker than what arrives, so the new thing is never waiting behind the old. */
    const val LEAVING = 120
}

/** What kind of change a [MotionContent] is making, which decides how it moves. */
enum class MotionKind {
    /** One screen for another, side by side in importance: the new one rises a little into place. */
    SCREEN,

    /** Going deeper and coming back: in from the right, and back out to it. */
    PAGE,

    /** One track's name for the next: the old one lifts away as the new one rises in. */
    TRACK,

    /** One icon for another in the same button, such as play and pause. */
    ICON,
}

/**
 * A tween at the shared pace when things move, and an instant jump when they do not.
 *
 * For the `animate*AsState` calls, which are the simple cases: a colour, a scale, a fade.
 */
@Composable
fun <T> motionSpec(durationMillis: Int = MotionTiming.STANDARD, delayMillis: Int = 0): FiniteAnimationSpec<T> =
    if (LocalMotion.current) tween(durationMillis, delayMillis, FastOutSlowInEasing) else snap()

/**
 * Content that changes with [target], moving the way [kind] describes.
 *
 * [forward] says, for a [MotionKind.PAGE], whether the change goes deeper -- in from the right -- or back
 * out. With animations off this is the plain swap it replaced.
 */
@Composable
fun <T> MotionContent(
    target: T,
    modifier: Modifier = Modifier,
    kind: MotionKind = MotionKind.SCREEN,
    forward: (from: T, to: T) -> Boolean = { _, _ -> true },
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (T) -> Any? = { it },
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    val moving = LocalMotion.current
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            if (!moving) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                transitionFor(kind, forward(initialState, targetState))
            }
        },
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        label = "motion-${kind.name.lowercase()}",
        content = content,
    )
}

private fun transitionFor(kind: MotionKind, forward: Boolean): ContentTransform {
    val (enter, exit) = when (kind) {
        MotionKind.SCREEN ->
            (
                fadeIn(tween(MotionTiming.STANDARD - 40, delayMillis = 40)) +
                    slideInVertically(tween(MotionTiming.STANDARD, easing = FastOutSlowInEasing)) { height -> height / 36 }
                ) to fadeOut(tween(MotionTiming.LEAVING))

        MotionKind.PAGE ->
            (
                slideInHorizontally(tween(MotionTiming.STANDARD + 20, easing = FastOutSlowInEasing)) { width ->
                    if (forward) width / 6 else -width / 6
                } + fadeIn(tween(MotionTiming.STANDARD - 40, delayMillis = 30))
                ) to (
                slideOutHorizontally(tween(MotionTiming.STANDARD, easing = FastOutSlowInEasing)) { width ->
                    if (forward) -width / 10 else width / 10
                } + fadeOut(tween(MotionTiming.LEAVING))
                )

        MotionKind.TRACK ->
            (
                slideInVertically(tween(MotionTiming.STANDARD, easing = FastOutSlowInEasing)) { height -> height / 2 } +
                    fadeIn(tween(MotionTiming.STANDARD))
                ) to (
                slideOutVertically(tween(MotionTiming.LEAVING + 40)) { height -> -height / 2 } +
                    fadeOut(tween(MotionTiming.LEAVING))
                )

        MotionKind.ICON ->
            (scaleIn(tween(MotionTiming.QUICK), initialScale = .6f) + fadeIn(tween(MotionTiming.QUICK))) to
                (scaleOut(tween(MotionTiming.QUICK), targetScale = .6f) + fadeOut(tween(MotionTiming.QUICK)))
    }
    // Unclipped, so a page sliding in is not cut off at the edge of the space the old one had.
    return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
}

/**
 * A small pop whenever [key] changes and [pop] says it should -- a heart being filled, say.
 *
 * Springs up past full size and settles back, which reads as the control having done something rather than
 * simply having changed colour. The first value is where things start, not a change, so nothing pops when a
 * screen merely opens on a liked track.
 */
@Composable
fun Modifier.popOn(key: Any?, pop: Boolean = true): Modifier {
    if (!LocalMotion.current) return this
    var popping by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) {
            first = false
        } else if (pop) {
            popping = true
            delay(MotionTiming.QUICK.toLong())
            popping = false
        }
    }
    val scale by animateFloatAsState(
        if (popping) 1.28f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pop",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The last value that was not null, kept.
 *
 * For a page that is sliding away after the thing it showed has already been let go of -- a playlist
 * closed, say. Without it the page goes blank as it leaves, which reads as a flicker rather than a slide.
 */
@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val kept = remember { arrayOfNulls<Any>(1) }
    if (value != null) kept[0] = value
    @Suppress("UNCHECKED_CAST")
    return kept[0] as T?
}
