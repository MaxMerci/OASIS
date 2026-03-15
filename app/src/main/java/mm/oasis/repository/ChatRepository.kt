package mm.oasis.repository

import mm.oasis.serialization.storage.ChatData

object ChatRepository : Repository<ChatData>("chats", ChatData.serializer()) {
    val currentChat: ChatData
        get() {
            var c = items.getOrNull(currentIndex)
            if (c == null) {
                addAt(0, emptyChat(), saveToStorage = false)
                c = items.first()
            }
            return c
        }

    init {
        val currentItems = items
        if (currentItems.isEmpty()) {
            updateState(listOf(emptyChat()), 0, saveToStorage = false)
        } else {
            val firstChat = currentItems.first()
            if (firstChat.messages.isNotEmpty()) {
                updateState(listOf(emptyChat()) + currentItems, 0, saveToStorage = false)
            } else {
                updateIndexInMemory(0)
            }
        }
    }

    private fun updateIndexInMemory(newIndex: Int) {
        val validated = newIndex.coerceIn(0, items.size - 1)
        updateState(items, validated, saveToStorage = false)
    }

    fun emptyChat(): ChatData {
        // я должен был оставить свой след
        val names = listOf(
            "обсуждение писек",
            "ЖОПА",
            "казахстан",
            "почта эпштейна",
            "ЭРП чатик",
            "фетишистский онлик",
            "дети толстых мам",
            "гей клуб ромашка",
            "фистинг клуб \"пальчики оближешь\""
        )
        val chatNames = mutableListOf<String>()
        for (i in 0..1000) {
            chatNames.add("Chat $i")
        }
        val name = listOf(names, chatNames).random()
        return ChatData(name.random())
    }
}