package app.morphe.patches.capcut

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private object NetworkInitHookFingerprint : Fingerprint(
    definingClass = "Lcom/vega/launcher/init/core/hook/NetworkInitHook;",
    name = "a",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
)

private object NetworkInitTaskFingerprint : Fingerprint(
    definingClass = "Lcom/vega/launcher/start/schedule/tasks/NetworkInitTask;",
    name = "run",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
)

private const val CRACKING_INTERCEPTOR_TYPE =
    "Lcom/vega/launcher/network/interceptors/CrackingInterceptor;"

private const val ASSIST_INTERCEPTOR_TYPE =
    "Lcom/vega/launcher/network/interceptors/AssistInterceptor;"

private fun replaceCrackingInterceptor(method: MutableMethod, patchName: String) {
    val instructions = method.implementation?.instructions?.toList()
        ?: error("[$patchName] Target ${method.name} has no instructions.")

    val newInstanceIdx = instructions.indexOfFirst { ins ->
        ins.opcode == Opcode.NEW_INSTANCE &&
            (ins as? ReferenceInstruction)?.reference.toString() == CRACKING_INTERCEPTOR_TYPE
    }
    if (newInstanceIdx < 0) {
        error("[$patchName] CrackingInterceptor new-instance not found in ${method.name}.")
    }

    val invokeDirectIdx = instructions.indices.firstOrNull { idx ->
        idx > newInstanceIdx &&
            instructions[idx].opcode == Opcode.INVOKE_DIRECT &&
            (instructions[idx] as? ReferenceInstruction)?.reference.toString().contains("CrackingInterceptor;-><init>")
    } ?: error("[$patchName] CrackingInterceptor <init> not found in ${method.name}.")

    val reg = (instructions[newInstanceIdx] as? OneRegisterInstruction)?.registerA
        ?: error("[$patchName] Could not resolve destination register in ${method.name}.")

    method.replaceInstruction(
        newInstanceIdx,
        "new-instance v$reg, $ASSIST_INTERCEPTOR_TYPE",
    )
    method.replaceInstruction(
        invokeDirectIdx,
        "invoke-direct {v$reg}, $ASSIST_INTERCEPTOR_TYPE-><init>()V",
    )
}

@Suppress("unused")
val capcutBlockUnsafeModalRenderPatch = bytecodePatch(
    name = "Block Unsafe Modal Render",
    description = "Suppresses the blocking security notice dialog on clean installs by neutralizing cracking verification interceptor registration in network initialization pipelines.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

    execute {
        var patched = 0

        replaceCrackingInterceptor(NetworkInitHookFingerprint.method, "Block Unsafe Modal Render")
        patched++

        replaceCrackingInterceptor(NetworkInitTaskFingerprint.method, "Block Unsafe Modal Render")
        patched++

        println("[Block Unsafe Modal Render] Applied $patched hooks -> CrackingInterceptor replaced with benign interceptor in network initialization pipelines.")
    }
}
