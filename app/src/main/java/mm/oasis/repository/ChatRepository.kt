package mm.oasis.repository

import mm.oasis.serialization.storage.ChatData

object ChatRepository : Repository<ChatData>("chats", ChatData.serializer()) {
    private var _currentChat: ChatData? = null

    var chats: MutableList<ChatData>
        get() = cache
        set(v) { cache = v }

    var currentChat: ChatData
        get() {
            if (_currentChat == null) {
                if (chats.isEmpty()) {
                    val newChat = ChatData(name = "New Chat")
                    chats.add(newChat)
                    currentIndex = 0
                    _currentChat = newChat
                } else {
                    if (currentIndex < 0 || currentIndex >= chats.size) currentIndex = 0
                    _currentChat = chats[currentIndex]
                }
            }
            return _currentChat!!
        }
        set(v) {
            val idx = chats.indexOf(v)
            if (idx != -1) {
                currentIndex = idx
                _currentChat = v
            }
        }
}