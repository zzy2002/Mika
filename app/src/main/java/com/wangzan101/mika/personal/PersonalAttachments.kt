package com.wangzan101.mika.personal

import com.wangzan101.mika.cap.sandbox.SandboxDirs
import com.wangzan101.mika.cap.sandbox.Attachments
import com.wangzan101.mika.cap.sandbox.AndroidSandbox
import com.wangzan101.mika.core.agent.AttachmentLines
import com.wangzan101.mika.core.workspace.GatedFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Keep the personal originals; each task receives its own writable copies. */
internal suspend fun <T> withPersonalAttachments(
    dirs: SandboxDirs,
    conversationId: String,
    text: String,
    files: List<Pair<String, String>>,
    freeBytes: () -> Long = { dirs.root.usableSpace },
    launch: (String) -> T,
): T {
    if (files.isEmpty()) return launch(text)
    return dirs.tree.shared {
        val created = mutableListOf<GatedFile>()
        try {
            val target = dirs.workspace(conversationId).apply { mkdirs() }
            var targetBytes = AndroidSandbox.treeBytes(target.toPath())
            val replacements = mutableMapOf<String, String>()
            val copied = files.map { (path, _) ->
                require(path.startsWith("/workspace/inbox/")) { "Invalid personal attachment" }
                dirs.gate.openRead(dirs.workspace(PERSONAL_WORKSPACE), path.removePrefix("/workspace/")).use { input ->
                    require(input.currentSize() <= Attachments.MAX_BYTES &&
                        freeBytes() >= Attachments.MIN_FREE + input.currentSize() &&
                        targetBytes <= Attachments.WORKSPACE_MAX - input.currentSize()) {
                        "Not enough space for task attachments"
                    }
                    val (_, output) = dirs.gate.createNumbered(target, "inbox", path.substringAfterLast('/'), max = 1000)
                    created += output
                    val buffer = ByteArray(64 * 1024)
                    val stream = input.input()
                    var size = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = stream.read(buffer)
                        if (n < 0) break
                        size += n
                        require(size <= Attachments.MAX_BYTES) { "Task attachment too large" }
                        output.write(if (n == buffer.size) buffer else buffer.copyOf(n))
                    }
                    val copiedPath = "/workspace/inbox/${output.name}"
                    targetBytes += size
                    replacements[path] = copiedPath
                    copiedPath to output.currentSize()
                }
            }
            val pattern = Regex(replacements.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) })
            val instruction = pattern.replace(text) { replacements.getValue(it.value) }
            launch(AttachmentLines.composeBytes(instruction, copied))
        } catch (e: Throwable) {
            created.forEach { it.delete() }
            throw e
        } finally {
            created.forEach { it.close() }
        }
    }
}

internal const val PERSONAL_WORKSPACE = "personal-main"
