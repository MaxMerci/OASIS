package mm.oasis.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import mm.oasis.remote.Storage

abstract class Repository<T>(
    private val name: String,
    private val itemSerializer: KSerializer<T>
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val storage = Storage(name)
    private val listSerializer = ListSerializer(itemSerializer)

    private val initialItems = storage.get(name, listSerializer) ?: emptyList()
    private val initialIndex = (storage.get("current_index", Int.serializer()) ?: 0).let {
        if (initialItems.isEmpty()) 0 else it.coerceIn(0, initialItems.size - 1)
    }

    private val _state = MutableStateFlow(RepositoryState(initialItems, initialIndex))
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
        repositoryScope.launch(Dispatchers.IO) {
            storage.put(name, stateToSave.items, listSerializer)
            storage.put("current_index", stateToSave.currentIndex, Int.serializer())
            storage.flush()
        }
    }
}