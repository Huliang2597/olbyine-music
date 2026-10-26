package com.olbyine.music.data

import androidx.compose.runtime.mutableStateMapOf

internal object ArtistAvatars {
    val map = mutableStateMapOf<String, String>()

    var resolve: (suspend (List<String>) -> Unit)? = null

    fun get(name: String): String? = map[name.trim()]?.takeIf { it.isNotBlank() }
}
