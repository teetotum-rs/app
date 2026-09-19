package io.github.teetotum_rs.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** Where a page stands: the first item shown, and how far it is scrolled past, in pixels. */
data class Position(val item: Int = 0, val offset: Int = 0) {
    /** As kept in the settings file: `item,offset`. */
    override fun toString() = "$item,$offset"

    companion object {
        /** A position written by [toString], or null for anything else. */
        fun parse(text: String): Position? {
            val (item, offset) = text.split(',').map(String::toIntOrNull).takeIf { it.size == 2 } ?: return null
            return if (item != null && offset != null) Position(item, offset) else null
        }
    }
}

/**
 * The scroll state of one page, kept while the app runs; [saved] is where the page stood when the app last
 * closed, and [onKeep] takes each position the page comes to rest at.
 */
class PageScroll(saved: Position, internal val onKeep: (Position) -> Unit) {
    val column = ScrollState(0)
    val list = LazyListState()

    /** The position still to go back to; a page read over Bluetooth only grows that tall once it has loaded. */
    private var pending: Position? = saved.takeIf { it != Position() }

    /**
     * Goes back to the pending position with [restore], which gives up once the user scrolls, then hands each
     * position [here] the page comes to rest at to [onKeep] until the page is left; [moving] tells a scroll going on.
     */
    internal suspend fun follow(here: () -> Position, moving: () -> Boolean, restore: suspend (Position) -> Unit) {
        try {
            pending?.let { restore(it) }
            pending = null
            snapshotFlow(moving).drop(1).filter { !it }.collect { onKeep(here()) }
        } finally {
            // A page shown again lays out short until its text is set, which would pull the position up to its end.
            if (pending == null) pending = here().takeIf { it != Position() }
        }
    }
}

/**
 * The scroll state of [page], one per page while the app runs: back where it was left, the first time where
 * [preferences] say the last run left it; [onPreferences] takes each position the page comes to rest at.
 */
@Composable
fun rememberPageScroll(page: Page, preferences: Preferences, onPreferences: (Preferences) -> Unit): PageScroll {
    val latest by rememberUpdatedState(preferences)
    val keep by rememberUpdatedState(onPreferences)
    val scrolls = remember { mutableMapOf<Page, PageScroll>() }
    return scrolls.getOrPut(page) {
        PageScroll(preferences.scroll[page] ?: Position()) {
            if (latest.scroll[page] != it) keep(latest.copy(scroll = latest.scroll + (page to it)))
        }
    }
}

/** The scroll state for a page that scrolls as one column, back where [scroll] was left; without, at the top. */
@Composable
internal fun pageScrollState(scroll: PageScroll?): ScrollState {
    if (scroll == null) return rememberScrollState()
    val state = scroll.column
    LaunchedEffect(scroll) {
        scroll.follow(here = { Position(0, state.value) }, moving = { state.isScrollInProgress }) { target ->
            // maxValue is Int.MAX_VALUE until the first layout.
            snapshotFlow { state.isScrollInProgress || state.maxValue in target.offset..<Int.MAX_VALUE }.first { it }
            if (!state.isScrollInProgress) state.scrollTo(target.offset)
        }
    }
    return state
}

/** The scroll state for a page that is a lazy list, back where [scroll] was left; without, at the top. */
@Composable
internal fun pageListState(scroll: PageScroll?): LazyListState {
    if (scroll == null) return rememberLazyListState()
    val state = scroll.list
    LaunchedEffect(scroll) {
        scroll.follow(
            here = { Position(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) },
            moving = { state.isScrollInProgress },
        ) { target ->
            snapshotFlow { state.isScrollInProgress || state.layoutInfo.totalItemsCount > target.item }.first { it }
            if (!state.isScrollInProgress) state.scrollToItem(target.item, target.offset)
        }
    }
    return state
}
