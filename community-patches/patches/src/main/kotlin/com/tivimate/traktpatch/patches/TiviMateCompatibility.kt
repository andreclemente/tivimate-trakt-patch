package com.tivimate.traktpatch.patches

internal const val TIVIMATE_PACKAGE_NAME = "ar.tvplayer.tv"

/**
 * Keep compatibility narrow. For now this uses package-level compatibility so
 * the scaffold compiles before stable version/fingerprint helpers are added.
 * Add explicit supported versions after patch fingerprints are proven.
 */
internal val tivimateCompatibility = TIVIMATE_PACKAGE_NAME
