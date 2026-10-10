/*
 * Copyright (C) 2024-2026 Piko contributors
 * Copyright (C) 2026 kveld9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Under Section 7(b) of GNU GPL version 3, you are required to preserve
 * the original author attributions and legal notices.
 */

package app.morphe.patches.twitter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants

private val quickPromoteEligibilityFingerprint = Fingerprint(
    strings = listOf(
        "x_lite_quick_promote_enabled",
        "x_lite_quick_promote_premium_paywall_enabled",
    ),
    returnType = "Z",
)

@Suppress("unused")
val twitterHidePromoteButtonPatch = bytecodePatch(
    name = "Hide Promote Button",
    description = "Hides promote button under self posts",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        quickPromoteEligibilityFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """.trimIndent(),
        )
        patched++

        println("[Hide Promote Button] Applied $patched hooks -> promote button suppressed.")
    }
}
