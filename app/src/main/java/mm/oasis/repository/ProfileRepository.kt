package mm.oasis.repository

import mm.oasis.serialization.storage.ProfileData

object ProfileRepository : Repository<ProfileData>("profiles", ProfileData.serializer()) {
    val currentProfile: ProfileData?
        get() = state.value.items.getOrNull(state.value.currentIndex)
}