
package com.example.photoreminder.data.model

import com.squareup.moshi.JsonClass

// Wrapper for GET /markers response
@JsonClass(generateAdapter = true)
data class GetMarkersResponse(
    val markers: List<MarkerDto>
)
