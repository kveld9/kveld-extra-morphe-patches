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

@Suppress("unused")
val twitterHideRecommendationItemsPatch = bytecodePatch(
    name = "Hide Recommendation Items",
    description = "Hides recommendation items such as Who to follow and Today's news in timeline, search, and replies",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        urtItemPresenterFingerprint.method.addInstructionsWithLabels(
            0,
            """
                move-object/from16 v0, p2
                if-nez v0, :cond_check_item
                goto :cond_continue

                :cond_check_item
                invoke-interface {v0}, Lcom/x/models/timelines/items/p0;->c()Ljava/lang/String;
                move-result-object v1
                if-eqz v1, :cond_continue

                :cond_check_entry_id
                const-string v2, "who-to-follow"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_rec

                const-string v2, "who_to_follow"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_rec

                const-string v2, "who-to-subscribe"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_rec

                const-string v2, "stories"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_rec

                const-string v2, "eventsummary"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_rec

                const-string v2, "toptabsrpusermodule"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_rec

                const-string v2, "community-to-join"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_rec

                goto :cond_continue

                :cond_suppress_rec
                sget-object v0, Lcom/x/urt/b0;->a:Lcom/x/urt/b0;
                return-object v0

                :cond_continue
                nop
            """.trimIndent(),
        )
        patched++

        println("[Hide Recommendation Items] Applied $patched hooks -> recommendation items suppressed.")
    }
}
