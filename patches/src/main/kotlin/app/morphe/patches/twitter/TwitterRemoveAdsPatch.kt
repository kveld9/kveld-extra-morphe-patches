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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants

private val urtItemPresenterFingerprint = Fingerprint(
    definingClass = "Lcom/x/urt/b;",
    name = "a",
    returnType = "Lcom/x/presenter/a;",
    parameters = listOf(
        "I",
        "Lcom/x/models/timelines/items/p0;",
        "Lcom/x/navigation/wl;",
        "Lcom/x/repositories/urt/t1;",
        "Lcom/x/urt/t0;",
    ),
)

private val googleMobileAdsInitFingerprint = Fingerprint(
    strings = listOf("DefaultMobileAdsInitializer"),
)

private val quickPromoteTrayFingerprint = Fingerprint(
    definingClass = "Lcom/x/urt/items/post/quickpromote/b;",
    name = "a",
    returnType = "V",
)

@Suppress("unused")
val twitterRemoveAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Removes promoted posts, trends and ads from timeline",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        // 1. Intercept timeline items before presenter dispatch in com.x.urt.b.a
        urtItemPresenterFingerprint.method.addInstructionsWithLabels(
            0,
            """
                move-object/from16 v0, p2
                if-nez v0, :cond_check_item
                goto :cond_continue

                :cond_check_item
                instance-of v1, v0, Lcom/x/models/timelines/items/q1;
                if-eqz v1, :cond_suppress_ad

                instance-of v1, v0, Lcom/x/models/timelines/items/q0;
                if-eqz v1, :cond_suppress_ad

                instance-of v1, v0, Lcom/x/models/timelines/items/k1;
                if-eqz v1, :cond_check_post
                goto :cond_not_post

                :cond_check_post
                move-object v1, v0
                check-cast v1, Lcom/x/models/timelines/items/k1;
                invoke-virtual {v1}, Lcom/x/models/timelines/items/k1;->y()Lcom/x/models/ef;
                move-result-object v1
                if-eqz v1, :cond_suppress_ad

                :cond_not_post
                instance-of v1, v0, Lcom/x/models/timelines/items/z1;
                if-eqz v1, :cond_check_trend
                goto :cond_not_trend

                :cond_check_trend
                move-object v1, v0
                check-cast v1, Lcom/x/models/timelines/items/z1;
                iget-object v1, v1, Lcom/x/models/timelines/items/z1;->a:Lcom/x/models/sf;
                if-eqz v1, :cond_check_sf
                goto :cond_not_trend

                :cond_check_sf
                invoke-virtual {v1}, Lcom/x/models/sf;->b()Lcom/x/models/ef;
                move-result-object v1
                if-eqz v1, :cond_suppress_ad

                :cond_not_trend
                invoke-interface {v0}, Lcom/x/models/timelines/items/p0;->c()Ljava/lang/String;
                move-result-object v1
                if-eqz v1, :cond_check_entry_id
                goto :cond_continue

                :cond_check_entry_id
                const-string v2, "promoted"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v1
                if-eqz v1, :cond_suppress_ad
                goto :cond_continue

                :cond_suppress_ad
                sget-object v0, Lcom/x/urt/b0;->a:Lcom/x/urt/b0;
                return-object v0

                :cond_continue
                nop
            """.trimIndent(),
        )
        patched++

        // 2. Short-circuit Google Mobile Ads SDK initialization
        googleMobileAdsInitFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """.trimIndent(),
        )
        patched++

        // 3. Suppress QuickPromote booster composable surface
        quickPromoteTrayFingerprint.method.addInstructions(
            0,
            "return-void",
        )
        patched++

        println("[Remove Ads] Applied $patched hooks -> promoted content and timeline ads suppressed.")
    }
}
