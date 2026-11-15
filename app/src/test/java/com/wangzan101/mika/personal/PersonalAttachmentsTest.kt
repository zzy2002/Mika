package com.wangzan101.mika.personal

import com.wangzan101.mika.cap.sandbox.Attachments
import com.wangzan101.mika.cap.sandbox.JVM_GATE
import com.wangzan101.mika.cap.sandbox.SandboxDirs
import com.wangzan101.mika.core.agent.AttachmentLines
import com.wangzan101.mika.core.workspace.SandboxTree
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PersonalAttachmentsTest {
    @get:Rule val temp = TemporaryFolder()
    private fun dirs() = SandboxDirs(temp.newFolder(), temp.newFolder(), temp.newFolder(), JVM_GATE, SandboxTree())

    @Test fun copiesOnlySelectedFilesAndPreservesOriginalsAndCollisions() = runBlocking {
        val dirs = dirs()
        val source = File(dirs.workspace(PERSONAL_WORKSPACE), "inbox").apply { mkdirs() }
        File(source, "report.txt").writeText("personal original")
        File(source, "private.txt").writeText("unrelated")
        val target = File(dirs.workspace("task"), "inbox").apply { mkdirs() }
        File(target, "report.txt").writeText("earlier task file")
        val path = "/workspace/inbox/report.txt"
        val result = withPersonalAttachments(dirs, "task", "Read $path", listOf(path to "17 B"), freeBytes = { Long.MAX_VALUE }) { it }
        val parsed = AttachmentLines.parse(result)
        val copied = parsed.files.single().first
        assertNotEquals(path, copied)
        assertEquals("Read $copied", parsed.words)
        assertEquals("personal original", File(dirs.workspace("task"), copied.removePrefix("/workspace/")).readText())
        assertEquals("earlier task file", File(target, "report.txt").readText())
        assertFalse(File(target, "private.txt").exists())
        assertEquals("personal original", File(source, "report.txt").readText())
    }

    @Test fun refusesCopiesBeforeLaunchingWhenTheTargetWorkspaceIsFull() = runBlocking {
        val dirs = dirs()
        File(dirs.workspace(PERSONAL_WORKSPACE), "inbox/report.txt").apply { parentFile!!.mkdirs(); writeText("data") }
        val existing = File(dirs.workspace("task"), "large.bin").apply { parentFile!!.mkdirs() }
        java.io.RandomAccessFile(existing, "rw").use { it.setLength(Attachments.WORKSPACE_MAX) }
        var launched = false
        val result = runCatching {
            withPersonalAttachments(dirs, "task", "read", listOf("/workspace/inbox/report.txt" to "4 B"), freeBytes = { Long.MAX_VALUE }) {
                launched = true
            }
        }
        assertTrue(result.isFailure)
        assertFalse(launched)
        assertEquals(Attachments.WORKSPACE_MAX, existing.length())
        assertFalse(File(dirs.workspace("task"), "inbox/report.txt").exists())
    }

    @Test fun failedLaunchRollsBackCopiesAndRetainsOriginal() = runBlocking {
        val dirs = dirs()
        val source = File(dirs.workspace(PERSONAL_WORKSPACE), "inbox/report.txt").apply { parentFile!!.mkdirs(); writeText("keep") }
        val result = runCatching {
            withPersonalAttachments(dirs, "task", "read", listOf("/workspace/inbox/report.txt" to "4 B"), freeBytes = { Long.MAX_VALUE }) {
                throw IOException("task refused")
            }
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("keep", source.readText())
        assertTrue(File(dirs.workspace("task"), "inbox").listFiles().orEmpty().isEmpty())
    }
}
