package io.github.svoka.sean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseVM<S: UiState,  E: UiEvent, A: UiAction> : ViewModel() {

    protected abstract fun getDefaultState(): S

    private val _uiState = MutableStateFlow(this.getDefaultState())

    val uiState: StateFlow<S> = _uiState.asStateFlow()

    //Read https://elizarov.medium.com/shared-flows-broadcast-channels-899b675e805c
    //to understand reasoning about decision
    private val _uiActions = Channel<A>()
    val uiActions: Flow<A> = _uiActions.receiveAsFlow()

    private val _uiNavActions = Channel<UiNavigationAction>()
    val uiNavActions: Flow<UiNavigationAction> = _uiNavActions.receiveAsFlow()


    private val uiEvents: MutableSharedFlow<E> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1
    )

    fun tryEmit(event: E)  {
        viewModelScope.launch(Dispatchers.IO) {
            uiEvents.emit(event)
        }
    }

    suspend fun emit(event: E)  {
        return uiEvents.emit(event)
    }

    //can only be triggered from VM
    protected suspend fun emit(action: A) {
        _uiActions.send(action)
    }

    //can only be triggered from VM
    protected suspend fun navigate(action: UiNavigationAction) {
        _uiNavActions.send(action)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            uiEvents.collect {
                val newState = stateReducer(it, _uiState.value)
                _uiState.emit(newState)
                sideEffects(it, newState)?.run {
                    uiEvents.emit(this)
                }
            }
        }
    }

    protected abstract suspend fun stateReducer(event: E, prevState: S): S
    protected abstract suspend fun sideEffects(event: E, newState: S): E?

    open fun onResume() {}
    open fun onPause() {}
}

interface UiState // Describe WHAT component Look like at the moment
interface UiEvent // Describe WHAT events component generates (also can be fired from VM)
interface UiAction // Actions from VM which should be DONE by components (mostly nav stuff and popups)

data class UiNavigationAction(val route: String)