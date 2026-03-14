package mm.oasis.repository

import mm.oasis.serialization.storage.ChatData

object ChatRepository : Repository<ChatData>("chats", ChatData.serializer()) {

    init {
        if (items.isEmpty()) {
            add(ChatData(name = "New Chat"))
        }
    }

    val currentChat: ChatData
        get() = items[currentIndex]

    fun selectChat(chat: ChatData) {
        val idx = items.indexOf(chat)
        if (idx != -1) {
            updateIndex(idx)
        }
    }

    fun updateCurrentChat(update: (ChatData) -> ChatData) {
        updateItem(currentIndex, update)
    }
}