package com.wangzan101.mika.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wangzan101.mika.ChineseUi
import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.agent.Turn
import com.wangzan101.mika.settings.Prerequisites
import com.wangzan101.mika.settings.ThemeAccent
import com.wangzan101.mika.takeover.Takeover
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.systemBarsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/** Unchanged capture fixture used on both base and PR; all conversation content is synthetic. */
@RunWith(AndroidJUnit4::class)
class DrawerComparisonCaptureTest {
    @get:Rule(order = Int.MIN_VALUE) val chinese = ChineseUi()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "drawer-comparison-" + UUID.randomUUID())
    private var clock = Instant.parse("2026-11-16T04:00:00Z").toEpochMilli()
    private val store = ConversationStore(directory, clock = { clock++ })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dark = mutableStateOf(false)
    private val actions = object : TaskActions {
        override fun start(goal: String, conversationId: String) {}
        override fun cancel() {}
        override fun resume() {}
        override fun takeOver() {}
        override fun handBack() {}
        override fun finishedMyself() {}
        override fun skip() {}
        override fun answer(text: String) {}
        override fun approve(key: Int) {}
        override fun decline(key: Int) {}
    }

    @After fun cleanUp() { scope.cancel(); directory.deleteRecursively() }

    @Test fun captureLightAndDarkDrawer() {
        store.append("reading", Turn("推荐几本适合周末读的书", "可以从一本短篇小说集开始。", "succeeded"))
        store.setTitle("reading", "周末阅读清单")
        store.append("english", Turn("陪我练习旅行英语", "Let's start with asking for directions.", "succeeded"))
        store.setTitle("english", "旅行英语练习")
        store.append("trip", Turn("帮我安排一个轻松的周末出行计划", "可以把行程留得宽松一些。\n\n**周六**\n\n- 上午去公园散步\n- 下午找一家安静的咖啡馆\n\n**周日**\n\n在家阅读和休息，给下周留一点余量。", "succeeded"))
        store.setTitle("trip", "周末出行计划")
        val vm = ChatViewModel(scope, MutableStateFlow(TaskState.Idle), MutableStateFlow(Takeover.Stage.None), actions, store, io = Dispatchers.Main.immediate)
        compose.runOnUiThread { vm.openConversation("trip"); compose.activity.systemBarsFor(false) }
        val fixedTime = Instant.parse("2026-11-16T04:00:00Z").toEpochMilli()
        listOf("reading", "english", "trip").forEachIndexed { index, id ->
            File(directory, "$id.json").setLastModified(fixedTime + index * 60_000)
        }
        compose.setContent {
            MikaTheme(dark = dark.value, accent = ThemeAccent.Blue) {
                ChatWithDrawer(
                    vm, Prerequisites.Check(emptyList()), reduceMotion = true,
                    conversations = { store.list() }, conversationChanges = store.changes,
                    conversationTitle = store::title, search = store.search, onSettings = {}, reopen = true,
                ) { open ->
                    ChatScreen(vm, Prerequisites.Check(emptyList()), MutableStateFlow(null), MutableStateFlow(false), reduceMotion = true, onOpenDrawer = open, openSettings = {})
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("conversation-row").fetchSemanticsNodes().size == 3 }
        val scenario = InstrumentationRegistry.getArguments().getString("captureScenario", "after")
        capture("$scenario-light")
        compose.runOnUiThread { dark.value = true; compose.activity.systemBarsFor(true) }
        capture("$scenario-dark")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output = File(context.getExternalFilesDir(null), "drawer-comparison").also { it.mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
