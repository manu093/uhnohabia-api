package com.sharedshoppinglists.app.presentation.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PendingInviteLinkTest : FunSpec({
    test("link https del backend") {
        PendingInviteLink.parse("https", "x.koyeb.app", listOf("invite", "AbC123")) shouldBe "AbC123"
    }
    test("esquema propio desde la pagina web") {
        PendingInviteLink.parse("uhnohabia", "invite", listOf("AbC123")) shouldBe "AbC123"
    }
    test("otros links se ignoran") {
        PendingInviteLink.parse("https", "x.koyeb.app", listOf("app", "download")).shouldBeNull()
        PendingInviteLink.parse("https", "x.koyeb.app", listOf("invite")).shouldBeNull()
        PendingInviteLink.parse(null, null, emptyList()).shouldBeNull()
    }
    test("ids con caracteres raros se rechazan") {
        PendingInviteLink.parse("https", "x", listOf("invite", "a/../b")).shouldBeNull()
        PendingInviteLink.parse("https", "x", listOf("invite", "x".repeat(100))).shouldBeNull()
    }
})
