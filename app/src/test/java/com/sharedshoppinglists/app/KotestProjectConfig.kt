package com.sharedshoppinglists.app

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.property.PropertyTesting

/**
 * Kotest project-level configuration.
 * Sets minimum 100 iterations for all property-based tests.
 */
class KotestProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        PropertyTesting.defaultIterationCount = 100
    }
}
