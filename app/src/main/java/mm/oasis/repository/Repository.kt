package mm.oasis.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import mm.oasis.remote.Storage

@Serializable
private class Index(val ids: List<String>, val current: Int)

private const val INDEX = "index"

abstract class Repository<T>(
    private val name: String,
    private val itemSerializer: KSerializer<T>,
    private val id: (T) -> String
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val storage = Storage(name)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<RepositoryState<T>> = _state.asStateFlow()

    val items: List<T> get() = _state.value.items
    val currentIndex: Int get() = _state.value.currentIndex
    val currentItem: T? get() = items.getOrNull(currentIndex)

    fun add(value: T, saveToStorage: Boolean = true) {
        val newItems = items + value
        val newIndex = newItems.size - 1
        updateState(newItems, newIndex, saveToStorage)
    }

    fun addAt(index: Int, value: T, saveToStorage: Boolean = true) {
        val validatedIndex = index.coerceIn(0, items.size)
        val newItems = items.toMutableList().apply {
            add(validatedIndex, value)
        }
        updateState(newItems, validatedIndex, saveToStorage)
    }

    fun remove(value: T, saveToStorage: Boolean = true) {
        val newItems = items.filter { it != value }
        val newIndex = if (newItems.isEmpty()) 0 else currentIndex.coerceIn(0, newItems.size - 1)
        updateState(newItems, newIndex, saveToStorage)
    }

    fun removeAt(index: Int, saveToStorage: Boolean = true) {
        if (index !in items.indices) return
        val newItems = items.toMutableList().apply { removeAt(index) }
        val newIndex = if (newItems.isEmpty()) 0 else currentIndex.coerceIn(0, newItems.size - 1)
        updateState(newItems, newIndex, saveToStorage)
    }

    fun updateIndex(newIndex: Int, saveToStorage: Boolean = true) {
        if (items.isEmpty()) return
        val validated = newIndex.coerceIn(0, items.size - 1)
        if (currentIndex != validated) {
            updateState(items, validated, saveToStorage)
        }
    }

    fun updateItem(index: Int, saveToStorage: Boolean = true, update: (T) -> T) {
        if (index !in items.indices) return
        val newItems = items.toMutableList()
        newItems[index] = update(newItems[index])
        updateState(newItems, currentIndex, saveToStorage)
    }

    protected fun updateState(newItems: List<T>, newIndex: Int, saveToStorage: Boolean = true) {
        val nextVersion = _state.value.version + 1
        _state.value = RepositoryState(newItems, newIndex, nextVersion)
        if (saveToStorage) {
            save()
        }
    }

    fun save() {
        val stateToSave = _state.value
        repositoryScope.launch(io) {
            try {
                write(stateToSave)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun load(): RepositoryState<T> {
        try {
            storage.migrate { old ->
                val items = old[name]?.let { storage.json.decodeFromString(ListSerializer(itemSerializer), it) }.orEmpty()
                write(RepositoryState(items, old["current_index"]?.toIntOrNull() ?: 0))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val index = storage.get(INDEX, Index.serializer()) ?: return RepositoryState()
        val items = index.ids.mapNotNull { storage.get(it, itemSerializer) }
        return RepositoryState(items, index.current.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }

    private fun write(state: RepositoryState<T>) {
        val ids = state.items.map(id)
        state.items.forEachIndexed { i, item -> storage.put(ids[i], item, itemSerializer) }
        storage.put(INDEX, Index(ids, state.currentIndex), Index.serializer())
        (storage.keys() - ids.toSet() - INDEX).forEach(storage::remove)
    }
}
