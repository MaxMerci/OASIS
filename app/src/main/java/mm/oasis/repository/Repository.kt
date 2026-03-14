package mm.oasis.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import mm.oasis.remote.Storage

abstract class Repository<T>(
    private val name: String,
    private val itemSerializer: KSerializer<T>
) {
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

    fun add(value: T) {
        val newItems = items + value
        val newIndex = newItems.size - 1
        updateState(newItems, newIndex)
    }

    fun remove(value: T) {
        val newItems = items.filter { it != value }
        val newIndex = if (newItems.isEmpty()) 0 else currentIndex.coerceIn(0, newItems.size - 1)
        updateState(newItems, newIndex)
    }

    fun remove(i: Int) {
        if (i !in items.indices) return
        val newItems = items.toMutableList().apply { removeAt(i) }
        val newIndex = if (newItems.isEmpty()) 0 else currentIndex.coerceIn(0, newItems.size - 1)
        updateState(newItems, newIndex)
    }

    fun updateIndex(newIndex: Int) {
        if (items.isEmpty()) return
        val validated = newIndex.coerceIn(0, items.size - 1)
        if (currentIndex != validated) {
            updateState(items, validated)
        }
    }

    fun updateItem(index: Int, update: (T) -> T) {
        if (index !in items.indices) return
        val newItems = items.toMutableList()
        newItems[index] = update(newItems[index])
        updateState(newItems, currentIndex)
    }

    private fun updateState(newItems: List<T>, newIndex: Int) {
        _state.value = RepositoryState(newItems, newIndex)
        save(newItems, newIndex)
    }

    private fun save(itemsToSave: List<T>, indexToSave: Int) {
        storage.put(name, itemsToSave, listSerializer)
        storage.put("current_index", indexToSave, Int.serializer())
        storage.flush()
    }
}