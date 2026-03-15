package com.geeksville.mesh.util

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

object ComposableUtil {

    @Composable
    fun rememberBooleanPreference(
        prefs: SharedPreferences,
        key: String,
        default: Boolean
    ): androidx.compose.runtime.State<Boolean> {

        val state = remember {
            mutableStateOf(prefs.getBoolean(key, default))
        }

        DisposableEffect(prefs, key) {

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    state.value = prefs.getBoolean(key, default)
                }
            }

            prefs.registerOnSharedPreferenceChangeListener(listener)

            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        return state
    }


}