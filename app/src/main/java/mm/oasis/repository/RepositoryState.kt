package mm.oasis.repository

data class RepositoryState<T>(
    val items: List<T> = emptyList(),
    val currentIndex: Int = 0,
    val version: Long = 0
)