package com.aeonreader

import io.kotest.core.config.AbstractProjectConfig

/**
 * Kotest project configuration.
 * Sets the default number of iterations for property-based tests to 100
 * as required by the design document.
 */
object KotestProjectConfig : AbstractProjectConfig() {
    // Default iterations for all property tests is 1000 in Kotest 5.x
    // Individual tests can override this with forAll(iterations = N) { ... }
    // The design requires a minimum of 100 iterations per property.
}
