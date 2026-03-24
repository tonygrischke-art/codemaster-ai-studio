package com.codemaster.aistudio.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {
    
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        handleException(throwable)
    }
    
    protected open fun handleException(throwable: Throwable) {
        // Override in subclasses to handle exceptions
        throwable.printStackTrace()
    }
    
    protected fun safeLaunch(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(exceptionHandler, block = block)
    }
}
