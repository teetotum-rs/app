package io.github.teetotum_rs.app

import androidx.compose.runtime.Composable

/** Keeps the screen on while this is in the composition, so a long transfer is not cut off by the phone sleeping. */
@Composable
expect fun KeepScreenOn()
