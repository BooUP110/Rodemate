package io.booup.roadmate.contracts

import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.StateFlow

interface PetTravelRepository {
    val state: StateFlow<PetTravelStorage>
    suspend fun load(): StorageResult<Unit>
    suspend fun update(transform: (PetTravelData) -> PetTravelData): StorageResult<Unit>
}
