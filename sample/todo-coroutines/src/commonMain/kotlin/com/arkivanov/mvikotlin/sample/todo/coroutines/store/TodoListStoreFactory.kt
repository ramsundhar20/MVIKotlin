package com.arkivanov.mvikotlin.sample.todo.coroutines.store

import com.arkivanov.mvikotlin.core.store.Executor
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.arkivanov.mvikotlin.sample.todo.common.database.TodoDatabase
import com.arkivanov.mvikotlin.sample.todo.common.internal.store.list.TodoListStore.Intent
import com.arkivanov.mvikotlin.sample.todo.common.internal.store.list.TodoListStore.State
import com.arkivanov.mvikotlin.sample.todo.common.internal.store.list.TodoListStoreAbstractFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class TodoListStoreFactory(
    storeFactory: StoreFactory,
    private val database: TodoDatabase,
    private val mainContext: CoroutineContext,
    private val ioContext: CoroutineContext
) : TodoListStoreAbstractFactory(
    storeFactory = storeFactory
) {

    override fun createExecutor(): Executor<Intent, Unit, State, Result, Nothing> = ExecutorImpl()

    private inner class ExecutorImpl : CoroutineExecutor<Intent, Unit, State, Result, Nothing>(mainContext = mainContext) {
        override fun executeAction(action: Unit, getState: () -> State) {
            scope.launch {
                val items = withContext(ioContext) { database.getAll() }
                dispatch(Result.Loaded(items))
            }
        }

        override fun executeIntent(intent: Intent, getState: () -> State) {
            when (intent) {
                is Intent.Delete -> delete(intent.id)
                is Intent.ToggleDone -> toggleDone(intent.id, getState)
                is Intent.AddToState -> dispatch(Result.Added(intent.item))
                is Intent.DeleteFromState -> dispatch(Result.Deleted(intent.id))
                is Intent.UpdateInState -> dispatch(Result.Changed(intent.id, intent.data))
            }.let {}
        }

        private fun delete(id: String) {
            dispatch(Result.Deleted(id))

            scope.launch(ioContext) {
                database.delete(id)
            }
        }

        private fun toggleDone(id: String, state: () -> State) {
            dispatch(Result.DoneToggled(id))

            val item = state().items.find { it.id == id } ?: return

            scope.launch(ioContext) {
                database.save(id, item.data)
            }
        }
    }
}
