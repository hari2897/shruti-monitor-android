package com.sangatkaar.android.model

data class TanpuraStringConfig(
    var velocity: Int = 100,
    var cents: Double = 0.0,
    var javari: Float = 50.0f
) {
    companion object {
        val DEFAULT_STRINGS = listOf(
            TanpuraStringConfig(100, 0.0, 50.0f),
            TanpuraStringConfig(100, 0.0, 50.0f),
            TanpuraStringConfig(100, 0.0, 50.0f),
            TanpuraStringConfig(100, 0.0, 50.0f),
            TanpuraStringConfig(100, 0.0, 50.0f)
        )

        val STRING_INDICES = intArrayOf(0, 1, 2, 3, 4)

        fun defaultVelocity(index: Int): Int = when(index) {
            0 -> 100
            1 -> 90
            2 -> 90
            3 -> 100
            else -> 100
        }
    }
}
