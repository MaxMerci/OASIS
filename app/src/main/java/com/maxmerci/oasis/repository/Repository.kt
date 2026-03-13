package com.maxmerci.oasis.repository

import com.maxmerci.oasis.remote.Storage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.max

abstract class Repository<T>(
    private val name: String,
    private val itemSerializer: KSerializer<T>
) {
    private val storage = Storage(name)
    private val listSerializer = ListSerializer(itemSerializer)

    @Suppress("UNCHECKED_CAST")
    var cache: MutableList<T> = (storage.get(name, listSerializer) ?: emptyList()).toMutableList()
    
    var currentIndex: Int = (storage.get("current_index", Int.serializer()) ?: 0).let {
        if (cache.isEmpty()) 0 else it.coerceIn(0, cache.size - 1)
    }

    fun add(value: T) {
        cache.add(value)
        save()
    }

    fun remove(value: T) {
        val i = cache.indexOf(value)
        if (i != -1) remove(i)
    }

    fun remove(i: Int) {
        if (i < 0 || i >= cache.size) return
        cache.removeAt(i)
        if (currentIndex >= cache.size) {
            currentIndex = max(0, cache.size - 1)
        }
        save()
    }

    fun save() {
        storage.put(name, cache, listSerializer)
        storage.put("current_index", currentIndex, Int.serializer())
        storage.flush()
    }
}