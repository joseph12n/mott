package dev.mott.app.data.standalone

import dev.mott.app.data.AppMode
import dev.mott.app.data.remote.MittApi

// The single api the repos talk to, chosen from the current mode:
//   MOBILE -> the phone's own local api (no hub, no network).
//   HUB    -> the Retrofit client for the paired hub, or null while unpaired.
//   UNSET  -> null, and neither builder is invoked (the lazies stay cold).
// Keeping the choice in one small object lets both the container and JVM
// tests decide the mode without a Context.
class ApiSelector(
    private val modeProvider: () -> AppMode,
    private val local: () -> MittApi,
    private val remoteProvider: () -> MittApi?,
) {
    fun select(): MittApi? = when (modeProvider()) {
        AppMode.MOBILE -> local()
        AppMode.HUB -> remoteProvider()
        AppMode.UNSET -> null
    }
}
