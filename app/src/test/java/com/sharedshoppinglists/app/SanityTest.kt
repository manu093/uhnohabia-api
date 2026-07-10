package com.sharedshoppinglists.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Sanity test to verify Kotest and property-based testing are configured correctly.
 */
class SanityTest : FunSpec({

    test("kotest is configured and runs") {
        1 + 1 shouldBe 2
    }

    test("property-based testing works with default iteration count") {
        checkAll(Arb.string(1..50)) { s ->
            s.length shouldBe s.length
        }
    }
})
