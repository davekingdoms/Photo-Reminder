
package com.example.photoreminder.data.model

import com.squareup.moshi.JsonClass

/** Wrapper per la risposta di GET /markers */
@JsonClass(generateAdapter = true)
data class GetMarkersResponse(
    val markers: List<MarkerDto>
)
