package com.github.svoka.sean

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


@Composable
inline fun <S: UiState, E: UiEvent, A: UiAction, reified V : BaseVM<S, E, A>> BaseScreenHolder(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    viewModel: V = hiltViewModel<V>(),
    navHostController: NavHostController,
    crossinline actionsHandler: (A) -> Unit,
    component: @Composable (S) -> Unit
) {

    val state: S by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        var actionsHandlerJob: Job? = null
        var navHandlerJob: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
                actionsHandlerJob?.cancel()
                actionsHandlerJob = scope.launch {
                    viewModel.uiActions.collect { actionsHandler(it) }
                }

                navHandlerJob?.cancel()
                navHandlerJob = scope.launch {
                    viewModel.uiNavActions.collect {
                        navHostController.navigate(it.route)
                    }
                }

            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.onPause()
                actionsHandlerJob?.cancel()
                navHandlerJob?.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    component(state)
}