package mm.oasis.repository

import mm.oasis.serialization.storage.ProfileData

object ProfileRepository : Repository<ProfileData>("profiles", ProfileData.serializer()) {
    // ох уж этот ООП
    var profiles
        get() = cache
        set(v) { cache = v }
    var currentProfile
        get() = cache[currentIndex]
        set(v) { currentIndex = cache.indexOf(v) }
}