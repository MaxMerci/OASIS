package mm.oasis.remote.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mm.oasis.remote.ApiClient
import mm.oasis.serialization.dto.EmbedRequest

class Embedding {
    suspend fun batchEmbed(inputs: List<String>): List<List<Double>> = withContext(Dispatchers.IO) {
        if (inputs.isEmpty()) return@withContext emptyList()
        val req = EmbedRequest(input = inputs)
        val response = ApiClient.embedding(req)
        response.data.map { it.embedding }
    }

    suspend fun embed(input: String): List<Double> =
        batchEmbed(listOf(input)).first()
}