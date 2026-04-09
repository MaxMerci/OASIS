package mm.oasis.remote.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mm.oasis.remote.ApiClient

class Embedding {
    suspend fun batchEmbed(inputs: List<String>): List<List<Double>> = withContext(Dispatchers.IO) {
//        if (inputs.isEmpty()) return@withContext emptyList()
//
//        val response = ApiClient.embedding(inputs)
//        response.data.map { it.embedding }
        // TODO
    }

    suspend fun embed(input: String): List<Double> =
        batchEmbed(listOf(input)).first()
}