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
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PATCH_TAG = "[Disable Auto Refresh]"
private val automaticFetchTypes = setOf("AUTO_REFRESH", "VIEWPORT_AWARE_AUTO_REFRESH")
private val fetchTypes = automaticFetchTypes + setOf("GAP", "NEWER_THAN", "OLDER_THAN", "PULL_TO_REFRESH")
private val ENUM_CONSTANT_FLAGS =
    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value or AccessFlags.ENUM.value

// Home timeline jump-to-top handler; anchors the scan below to the home timeline.
private val autoRefreshDispatcherFingerprint = Fingerprint(
    returnType = "V",
    strings = listOf(
        "x_lite_infra_home_jtt_clear_cache_enabled",
        "home_timeline_navigation_clear_cache_after_manual_jtt_enabled",
    ),
)

// Timeline fetch type enum (com.x.models.timelines.d in 12.34.0 and 12.35.0), found by its constants.
// Its static initializer holds each constant's name as a string, so the string index yields the candidates.
private fun BytecodePatchContext.fetchTypeEnum(): String {
    val candidates = classDefByStrings(fetchTypes.first()).filter { classDef ->
        classDef.superclass == "Ljava/lang/Enum;" &&
            classDef.staticFields.filter { it.accessFlags == ENUM_CONSTANT_FLAGS && it.type == classDef.type }
                .map { it.name }.containsAll(fetchTypes)
    }.map { it.type }
    return candidates.singleOrNull()
        ?: error("$PATCH_TAG Expected one enum with constants $fetchTypes, found $candidates.")
}

private fun ReferenceInstruction.isAutomaticFetchSget(enum: String): Boolean {
    val fieldRef = reference as? FieldReference ?: return false
    return fieldRef.type == enum && fieldRef.name in automaticFetchTypes
}

// Timeline repository interface fetch(type, cursor): `r1.k` in 12.34.0, `r1.r` in 12.35.0.
private fun ReferenceInstruction.isTimelineFetchInvoke(enum: String): Boolean {
    val methodRef = reference as? MethodReference ?: return false
    return methodRef.returnType == "V" && methodRef.parameterTypes.firstOrNull()?.toString() == enum
}

private fun Method.automaticFetchInvokes(enum: String): List<Int> {
    val instructions = implementation?.instructions?.toList() ?: return emptyList()
    val sgetIndices = instructions.indices.filter { i ->
        instructions[i].opcode == Opcode.SGET_OBJECT &&
            (instructions[i] as ReferenceInstruction).isAutomaticFetchSget(enum)
    }
    return sgetIndices.mapNotNull { sgetIndex ->
        ((sgetIndex + 1)..minOf(sgetIndex + 3, instructions.lastIndex)).firstOrNull { i ->
            instructions[i].opcode == Opcode.INVOKE_INTERFACE &&
                (instructions[i] as ReferenceInstruction).isTimelineFetchInvoke(enum)
        }
    }.distinct()
}

// Home dispatcher, home worker coroutine and pager callback; R8 merges the last two into unrelated classes.
private fun automaticFetchFingerprint(enum: String) = Fingerprint(
    custom = { method, _ -> method.automaticFetchInvokes(enum).isNotEmpty() },
)

private fun nopAutomaticFetches(method: MutableMethod, enum: String): Int {
    val targetInvokeIndices = method.automaticFetchInvokes(enum)
    targetInvokeIndices.forEach { method.replaceInstruction(it, "nop") }
    println("$PATCH_TAG Removed ${targetInvokeIndices.size} automatic fetch(es) in '${method.definingClass}->${method.name}'.")
    return targetInvokeIndices.size
}

@Suppress("unused")
val twitterDisableAutoRefreshPatch = bytecodePatch(
    name = "Disable Auto Refresh",
    description = "Stops the home timeline from automatically jumping to new posts.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val enum = fetchTypeEnum()
        val dispatcher = autoRefreshDispatcherFingerprint.method
        val matches = automaticFetchFingerprint(enum).matchAllOrNull().orEmpty()
        if (matches.none { it.method.definingClass == dispatcher.definingClass && it.method.name == dispatcher.name }) {
            error("$PATCH_TAG Home timeline dispatcher '${dispatcher.definingClass}->${dispatcher.name}' has no automatic fetch call site.")
        }
        matches.forEach { patched += nopAutomaticFetches(it.method, enum) }

        println("[Disable Auto Refresh] Applied $patched hooks -> timeline auto refresh disabled.")
    }
}
