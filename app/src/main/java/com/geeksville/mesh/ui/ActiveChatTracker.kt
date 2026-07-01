package com.geeksville.mesh.ui

import kotlinx.coroutines.flow.MutableStateFlow

object ActiveChatTracker {

    private val _activeContactKey = MutableStateFlow<String?>(null)

    internal fun setActiveChat(contactKey: String) {
        _activeContactKey.value = contactKey
    }

    internal fun clearActiveChat(contactKey: String) {
        if (_activeContactKey.value == contactKey) {
            _activeContactKey.value = null
        }
    }

    fun isChatActiveAndFocused(contactKey: String): Boolean {
        return _activeContactKey.value == contactKey
    }
}