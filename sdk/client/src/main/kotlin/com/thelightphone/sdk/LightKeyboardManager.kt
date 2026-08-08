package com.thelightphone.sdk

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.error
import com.thelightphone.sdk.shared.getOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "LightKeyboardManager"

data class KeyboardOptions(
    val emojis: List<String> = emptyList(),
    val displayReturn: Boolean = true,
    val displayVoice: Boolean = true,
    val enableKeyAnimation: Boolean = true,
    val swipeEnabled: Boolean = false,
)

suspend fun refreshKeyboardOptions(): KeyboardOptions? {
    val result =
        callRemoteServiceMethod(LightServiceMethod.GetKeyboardOptions, Unit).let { result ->
            result.error?.let {
                Log.e(TAG, "Error getting keyboard options, code:${it.code}, message:${it.extra}")
                return null
            }
            val options = result.getOrNull()
            if (options == null) {
                Log.e(TAG, "Keyboard options returned null")
                return null
            }
            options
        }

    return KeyboardOptions(
        emojis = emptyList(),
        displayReturn = true,
        displayVoice = result.displayVoice ?: true,
        enableKeyAnimation = result.enableKeyAnimation ?: true,
        swipeEnabled = result.swipeEnabled == true,
    )
}

private var cachedOptions = KeyboardOptions()

@Composable
fun rememberKeyboardOptions(
    initialOptions: KeyboardOptions = cachedOptions
): StateFlow<KeyboardOptions> {
    val flow = remember { MutableStateFlow(initialOptions) }
    val scope = rememberCoroutineScope()
    val refreshJob = remember { mutableStateOf<Job?>(null) }

    SideEffect {
        refreshJob.value?.cancel()
        refreshJob.value = scope.launch {
            refreshKeyboardOptions()?.let {
                cachedOptions = it
                flow.value = it
            }
        }
    }
    return flow
}
