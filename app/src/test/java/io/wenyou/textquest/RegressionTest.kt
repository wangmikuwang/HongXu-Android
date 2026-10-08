package io.wenyou.textquest

import io.wenyou.textquest.data.ai.withContinuity
import io.wenyou.textquest.data.ai.AiCreator
import io.wenyou.textquest.data.ai.CreationKind
import io.wenyou.textquest.data.ai.AiDirector
import io.wenyou.textquest.data.engine.GameEngine
import io.wenyou.textquest.data.llm.*
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.data.repo.LocalLibrary
import io.wenyou.textquest.data.repo.ShareCode
import io.wenyou.textquest.ui.vm.PlayViewModel
import io.wenyou.textquest.ui.vm.PlayStage
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import androidx.compose.ui.graphics.luminance
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class RegressionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun usageFramesPricesAndPersistenceRemainAccurate() = runBlocking {
        val p = ApiProfile("p", "test", baseUrl = "http://localhost/v1", model = "test", inputPrice = 2.0, outputPrice = 4.0, cachedPrice = 0.5, cacheWritePrice = 3.0)
        val usage = TokenUsage().read(ProviderKind.OPENAI_COMPAT, AppJson.parseToJsonElement("""{"usage":{"prompt_tokens":1000,"completion_tokens":200,"prompt_cache_hit_tokens":400}}"""))
        assertEquals(0.0022, usage.cost(p)!!, 0.00000001)
        assertNull(usage.cost(p.copy(cachedPrice = null)))
        assertNull(usage.cost(p.copy(inputPrice = -1.0)))
        assertNull(TokenUsage().cost(p))
        var a = TokenUsage().read(ProviderKind.ANTHROPIC, AppJson.parseToJsonElement("""{"message":{"usage":{"input_tokens":10,"cache_read_input_tokens":20,"cache_creation_input_tokens":30,"output_tokens":1}}}"""))
        a = a.read(ProviderKind.ANTHROPIC, AppJson.parseToJsonElement("""{"usage":{"output_tokens":8}}"""))
        assertEquals(TokenUsage(60, 8, 20, 30), a)
        assertEquals(TokenUsage(20, 13, 4), TokenUsage().read(ProviderKind.GEMINI, AppJson.parseToJsonElement("""{"usageMetadata":{"promptTokenCount":20,"candidatesTokenCount":10,"thoughtsTokenCount":3,"cachedContentTokenCount":4}}""")))
        val file = File(temp.newFolder(), "usage.json")
        val tracker = UsageTracker(file)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = """data: {"choices":[{"delta":{"content":"answer"}}]}

data: {"choices":[],"usage":{"prompt_tokens":1000,"completion_tokens":200,"prompt_cache_hit_tokens":400}}

data: [DONE]

"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        try {
            ChatClient(client, tracker).streamText(p, "", "")
            assertTrue(tracker.active.value.isEmpty())
            assertEquals(usage, tracker.records.value.single().tokens)
            assertEquals("完成", tracker.records.value.single().status)
            assertEquals(tracker.records.value, UsageTracker(file).records.value)
            assertFalse(file.readText().contains("apiKey"))
            try { ChatClient(client, tracker).streamText(p.copy(model = ""), "", "") } catch (_: Exception) { }
            assertEquals("失败", tracker.records.value.last().status)
            assertNull(tracker.records.value.last().estimatedCost)
            assertTrue(tracker.active.value.isEmpty())
            val slow = OkHttpClient.Builder().addInterceptor { chain ->
                Thread.sleep(300)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("{}".toResponseBody()).build()
            }.build()
            try {
                val job = launch { ChatClient(slow, tracker).streamText(p, "", "") }
                withTimeout(3000) { tracker.active.first { it.isNotEmpty() } }
                job.cancelAndJoin()
                assertEquals("已取消", tracker.records.value.last().status)
                assertNull(tracker.records.value.last().estimatedCost)
                assertTrue(tracker.active.value.isEmpty())
            } finally { slow.dispatcher.executorService.shutdownNow(); slow.connectionPool.evictAll() }
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun continuitySurvivesSaveAndReachesBothAiModes() = runBlocking {
        val director = AiDirector(ChatClient(OkHttpClient()))
        val scene = director.parseScene("""{"text":"一起调查","memory":"约定明日在钟楼见面","relationships":[{"from":"a","to":"b","description":"盟友","reason":"共享线索"},{"from":"a","to":"missing","description":"无效"},{"from":"a","to":"a","description":"无效"}]}""")
        val state = scene.withContinuity(SessionState("s"), setOf("a", "b"))
        assertEquals("约定明日在钟楼见面", state.memory)
        assertEquals(mapOf("b" to "盟友（共享线索）"), state.characterStates.getValue("a").relationships)
        assertEquals(state, AppJson.decodeFromString(SessionState.serializer(), AppJson.encodeToString(SessionState.serializer(), state)))
        assertEquals(state.memory, director.parseScene("""{"text":"后续"}""").withContinuity(state, setOf("a", "b")).memory)
        assertEquals("", AppJson.decodeFromString(SessionState.serializer(), """{"storyId":"s"}""").memory)
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer(); chain.request().body!!.writeTo(buffer); requests.add(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"choices":[{"message":{"content":"{\"text\":\"继续\",\"choices\":[{\"text\":\"调查\"}]}"}}]}""".toResponseBody()).build()
        }.build()
        try {
            val ai = AiDirector(ChatClient(client))
            val story = Story("s", "test", characterIds = listOf("a", "b"))
            val characters = listOf(CharacterData("a", "甲"), CharacterData("b", "乙"))
            val old = state.copy(playerCharacterId = "a", playerCharacterName = "甲", history = List(150) { LogEntry(text = "新回合$it") })
            val profile = ApiProfile("p", "test", baseUrl = "http://localhost/v1", model = "test")
            ai.generateScene(profile, story, StoryNode("start"), characters, old)
            ai.directorTurn(profile, story, characters, old, "继续")
            assertEquals(2, requests.size)
            requests.forEach { assertTrue(it.contains(state.memory)); assertTrue(it.contains("盟友")); assertTrue(it.contains("玩家扮演：甲")); assertTrue(it.contains("不替玩家决定")) }
            assertTrue(ai.playerIdentity(old, emptyList()).contains("玩家扮演：甲"))
            assertTrue(ai.playerIdentity(SessionState("s"), characters).contains("自由身份"))
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun oneLineCreationProducesPlayableLinkedContentAndRejectsBrokenDrafts() = runBlocking {
        val creator = AiCreator(ChatClient())
        val json = """{"story":{"title":"雨城","worldSummary":"寻找记忆","opening":"她说：\"你是谁？\" {旧物}"},"characters":[{"name":" 少女 ","personality":"敏锐","background":"旧物店店主"}]}"""
        val draft = creator.parse("```json\n$json\n```", CreationKind.STORY)
        val story = draft.stories.single()
        assertEquals(StoryMode.AI_DIRECTOR, story.mode)
        assertEquals(draft.characters.map { it.id }, story.characterIds)
        assertEquals("少女", draft.characters.single().name)
        assertEquals("她说：\"你是谁？\" {旧物}", story.nodes.getValue(story.startNodeId).text)
        assertFalse(creator.parse(json, CreationKind.STORY).stories.single().id == story.id)
        assertTrue(creator.parse(json, CreationKind.CHARACTERS).stories.isEmpty())
        for (broken in listOf("{}", json.dropLast(1), json.replace("少女", " "), json.replace("敏锐", ""))) {
            assertTrue(runCatching { creator.parse(broken, CreationKind.STORY) }.isFailure)
        }
        assertTrue(runCatching { creator.parse(json.replace("\"background\"", "\"adult\":true,\"background\""), CreationKind.STORY, adultContent = false) }.isFailure)
        val folder = temp.newFolder()
        val library = LocalLibrary(folder)
        library.upsertStory(Story("existing", "旧剧情"))
        library.importShared(draft)
        library.importShared(draft)
        assertEquals(2, library.stories.value.size)
        assertEquals(1, library.characters.value.size)
        assertEquals(story, LocalLibrary(folder).stories.value.first { it.id == story.id })
    }

    @Test fun appleThemeHasSafeDefaultsAndReadableColors() {
        assertEquals(io.wenyou.textquest.ui.theme.ThemeStyle.MATERIAL, io.wenyou.textquest.ui.theme.ThemeStyle.fromStored("unknown"))
        assertEquals(io.wenyou.textquest.ui.theme.ThemeStyle.APPLE, io.wenyou.textquest.ui.theme.ThemeStyle.fromStored("APPLE"))
        for (dark in listOf(false, true)) {
            val colors = io.wenyou.textquest.ui.theme.appleColors(dark)
            for ((foreground, background) in listOf(colors.onSurface to colors.surface, colors.onPrimary to colors.primary)) {
                val a = foreground.luminance()
                val b = background.luminance()
                assertTrue((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f)
            }
        }
    }

    @Test fun aiStateChangesSurviveParsing() {
        val scene = AiDirector(ChatClient()).parseScene(
            """{"text":"门开了","choices":[{"text":"进入[to:hall]"}],"state":[{"char":"c","metric":"trust","delta":5}]}"""
        )
        assertEquals("hall", scene.choices.single().next)
        assertEquals(5.0, scene.stateEffects.single().delta, 0.0)
    }

    @Test fun aiNarrationDialogueAndReasoningRemainSeparate() {
        val director = AiDirector(ChatClient())
        val scene = director.parseScene("""{
            "entries":[{"text":"**门开了。**"},{"speakerId":"c","text":"欢迎回来。"},
                {"text":"他放下了灯。"},{"speakerId":"missing","text":"远处传来声音。"},
                {"speaker":"守门人","text":"请进。"}],
            "choices":[{"text":"进入[to:hall]"}],"state":[{"char":"c","metric":"trust","delta":5}]
        }""").copy(reasoning = "provider reasoning")
        val logs = scene.logEntries(listOf(CharacterData("c", "烛影")))
        assertEquals(listOf(EntryKind.NARRATION, EntryKind.CHARACTER, EntryKind.NARRATION, EntryKind.NARRATION, EntryKind.CHARACTER), logs.map { it.kind })
        assertEquals("门开了。", logs.first().text)
        assertEquals("烛影", logs[1].speaker)
        assertEquals("c", logs[1].speakerId)
        assertEquals("欢迎回来。", logs[1].text)
        assertEquals("守门人", logs.last().speaker)
        assertEquals(listOf("provider reasoning", "", "", "", ""), logs.map { it.reasoning })
        assertEquals("hall", scene.choices.single().next)
        assertEquals(5.0, scene.stateEffects.single().delta, 0.0)
        val state = SessionState("s", history = logs)
        assertEquals(state, AppJson.decodeFromString(SessionState.serializer(), AppJson.encodeToString(SessionState.serializer(), state)))
        assertEquals("旧正文", director.parseScene("""{"text":"旧正文"}""").logEntries(emptyList()).single().text)
    }

    @Test fun proseAfterBodyMarkerIsPreserved() {
        val scene = AiDirector(ChatClient()).parseScene("我先构思场景\n【正文】\n门开了。\n----\n他走了进来。")
        assertEquals("门开了。\n\n他走了进来。", scene.text)
    }

    @Test fun reasoningOnlyResponseNeverBecomesNarration() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(
                    """{"choices":[{"message":{"reasoning_content":"provider reasoning"}}]}""".toResponseBody()
                ).build()
        }.build()
        try {
            val failure = runCatching {
                AiDirector(ChatClient(client)).directorTurn(
                    ApiProfile("p", "test", baseUrl = "http://localhost/v1", model = "test"),
                    Story("s", "story"), emptyList(), SessionState("s"), "继续"
                )
            }.exceptionOrNull()
            assertTrue(failure is io.wenyou.textquest.data.llm.LlmException)
            assertTrue(failure?.message.orEmpty().contains("未返回剧情正文"))
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test fun shareCodesRejectTruncatedAndOversizedPayloads() {
        val bundle = AppBundle(characters = listOf(CharacterData("c", "角色")))
        assertEquals(bundle.copy(origin = BuildConfig.SHARE_ORIGIN), ShareCode.decode(ShareCode.encode(bundle)))
        val legacy = Base64.getUrlEncoder().withoutPadding().encodeToString(
            AppJson.encodeToString(AppBundle.serializer(), bundle).toByteArray(Charsets.UTF_8))
        assertEquals(bundle, ShareCode.decode("WY1:$legacy"))
        fun compressed(bytes: ByteArray): ByteArray {
            val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
            try {
                val out = ByteArrayOutputStream()
                DeflaterOutputStream(out, deflater).use { it.write(bytes) }
                return out.toByteArray()
            } finally { deflater.end() }
        }
        fun code(bytes: ByteArray) = "WY2:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val valid = compressed("{}".toByteArray())
        assertEquals(AppBundle(), ShareCode.decode(code(valid)))
        assertNull(ShareCode.decode(code(valid.copyOf(valid.size - 1))))
        assertNull(ShareCode.decode(code(compressed(ByteArray(8 * 1024 * 1024 + 1) { 32 }))))
        assertEquals("", ShareCode.encode(AppBundle(characters = listOf(
            CharacterData("c", "large", background = "x".repeat(8 * 1024 * 1024))
        ))))
    }

    @Test fun qrChunksCarryBookIdentityAndRemainBackwardCompatible() {
        val first = ShareCode.qrChunks("a".repeat(900))
        val second = ShareCode.qrChunks("b".repeat(900))
        assertEquals(3, first.size)
        assertNotEquals(ShareCode.parseChunk(first[0])!!.bookId, ShareCode.parseChunk(second[0])!!.bookId)
        val parsed = first.mapNotNull(ShareCode::parseChunk)
        assertEquals("a".repeat(900), ShareCode.assembleChunks(parsed.associate { it.index to it.data }, parsed[0].total))
        assertEquals(ShareCode.QrChunk(1, 2, "old"), ShareCode.parseChunk("wyq:1/2|old"))
    }

    @Test fun albumQrSelectionAssemblesOneCompleteBookInAnyOrder() {
        val noisy = ByteArray(4000).also { java.util.Random(1).nextBytes(it) }
        val code = ShareCode.encode(AppBundle(characters = listOf(
            CharacterData("c", "角色", background = Base64.getEncoder().encodeToString(noisy))
        )))
        val chunks = ShareCode.qrChunks(code)
        assertTrue(chunks.size > 1)
        assertEquals(code, ShareCode.assembleQrTexts(chunks.reversed()))
        assertNull(ShareCode.assembleQrTexts(chunks.dropLast(1)))

        val other = ShareCode.qrChunks(ShareCode.encode(AppBundle(stories = listOf(Story("s", "剧情")))))
        assertNull(ShareCode.assembleQrTexts(other + chunks.reversed()))
    }

    @Test fun repeatedSharedImportReportsExistingContent() = runBlocking {
        val library = LocalLibrary(temp.newFolder())
        val bundle = AppBundle(
            characters = listOf(CharacterData("c", "角色", bottomRuleIds = listOf("r"))),
            stories = listOf(Story("s", "剧情", characterIds = listOf("c"))),
            bottomRules = listOf(BottomRule("r", "规则", "内容"))
        )
        assertEquals(LocalLibrary.SharedImportResult(3, 0), library.importShared(bundle))
        assertEquals(LocalLibrary.SharedImportResult(0, 3), library.importShared(bundle))
    }

    @Test fun alphaDiversePresetIsValidAndSelfContained() {
        val file = listOf(
            File("src/alpha/assets/presets/wenyou-diverse-presets.json"),
            File("app/src/alpha/assets/presets/wenyou-diverse-presets.json")
        ).first(File::isFile)
        val bundle = AppJson.decodeFromString(AppBundle.serializer(), file.readText())
        assertEquals(6, bundle.characters.size)
        assertEquals(3, bundle.stories.size)
        assertEquals(
            setOf(SexualOrientation.LESBIAN, SexualOrientation.PAN, SexualOrientation.ASEXUAL),
            bundle.characters.map { it.orientation }.toSet()
        )
        val characterIds = bundle.characters.map { it.id }.toSet()
        assertTrue(bundle.characters.all { it.lgbt && !it.adult })
        assertTrue(bundle.stories.all { it.lgbt && !it.adult && it.characterIds.all(characterIds::contains) })
    }

    @Test fun alphaAdultDiversePresetCoversNonStraightOrientations() {
        val file = listOf(
            File("src/alpha/assets/presets/wenyou-adult-diverse-presets.json"),
            File("app/src/alpha/assets/presets/wenyou-adult-diverse-presets.json")
        ).first(File::isFile)
        val bundle = AppJson.decodeFromString(AppBundle.serializer(), file.readText())
        assertEquals(10, bundle.characters.size)
        assertEquals(5, bundle.stories.size)
        assertEquals(
            setOf(
                SexualOrientation.GAY, SexualOrientation.LESBIAN, SexualOrientation.BI,
                SexualOrientation.PAN, SexualOrientation.ASEXUAL
            ),
            bundle.characters.map { it.orientation }.toSet()
        )
        // 成人包必须同时受「成人内容」与「显示 LGBT」两个开关约束
        assertTrue(bundle.characters.all { it.adult && it.lgbt })
        assertTrue(bundle.characters.all { it.initial.metrics.isNotEmpty() })
        assertTrue(bundle.stories.all { it.adult && it.lgbt })
        val characterIds = bundle.characters.map { it.id }.toSet()
        assertTrue(bundle.stories.all { it.characterIds.isNotEmpty() && it.characterIds.all(characterIds::contains) })
        // 每部剧情都要有可渲染的开场节点，并声明成人尺度与自愿边界
        assertTrue(bundle.stories.all { it.nodes.containsKey(it.startNodeId) })
        assertTrue(bundle.stories.all { it.ai.directorExtra.contains("18+") })
    }

    @Test fun missingCharacterDoesNotReadGlobalVariablesOrFlags() {
        val state = SessionState("s", variables = mapOf("trust" to 99.0), flags = setOf("met"))
        assertFalse(GameEngine.evaluate(state, listOf(Cond(name = "trust", value = 50.0, charId = "missing"))))
        assertFalse(GameEngine.evaluate(state, listOf(Cond(CondType.FLAG_TRUE, "met", charId = "missing"))))
        assertTrue(GameEngine.evaluate(state, listOf(Cond(CondType.FLAG_FALSE, "met", charId = "missing"))))
    }

    @Test fun responsePreservesBothReasoningAndContent() = runBlocking {
        for (body in listOf(
            "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thought\",\"content\":\"answer\"}}]}\n\ndata: [DONE]\n\n",
            """{"choices":[{"message":{"reasoning_content":"thought","content":"answer"}}]}"""
        )) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(body.toResponseBody()).build()
            }.build()
            try {
                val content = StringBuilder()
                val reasoning = StringBuilder()
                val result = ChatClient(client).streamText(
                    ApiProfile("p", "test", baseUrl = "http://localhost/v1", model = "test"), "", "",
                    onDelta = { content.append(it) }, onReasoning = { reasoning.append(it) }
                )
                assertEquals("answer", result.content)
                assertEquals("thought", result.reasoning)
                assertEquals("answer", content.toString())
                assertEquals("thought", reasoning.toString())
            } finally {
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test fun concurrentWritesPreserveEveryRecord() = runBlocking {
        val dir = temp.newFolder()
        val library = LocalLibrary(dir)
        coroutineScope {
            repeat(80) { i -> launch(Dispatchers.Default) {
                library.upsertCharacter(CharacterData("c$i", "Character $i"))
            } }
        }
        assertEquals(80, library.characters.value.size)
        assertEquals(library.characters.value, LocalLibrary(dir).characters.value)
    }

    @Test fun choiceHistoryAndLoadedNarrationRemainConsistent() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val library = LocalLibrary(temp.newFolder())
            val story = Story("s", "story", nodes = mapOf(
                "start" to StoryNode("start", text = "开场", choices = listOf(
                    ChoiceData("留下", "@self", effects = listOf(Effect(EffectType.ADD_VAR, "count", 1.0))),
                    ChoiceData("离开", "end", effects = listOf(Effect(EffectType.ROLL, "die", to = 6.0)))
                )),
                "end" to StoryNode("end", NodeKind.ENDING, title = "结局标题", text = "结局")
            ))
            library.upsertStory(story)
            val director = AiDirector(ChatClient())
            val vm = PlayViewModel("s", "new", library, director) { null }
            runCurrent()
            vm.selectPlayerCharacter("")
            vm.chooseAuthored(0)
            assertEquals(listOf("开场", "留下"), vm.ui.value.session!!.history.map { it.text })
            assertEquals(1.0, vm.ui.value.session!!.variables["count"]!!, 0.0)
            val state = vm.ui.value.session!!
            library.upsertSave(SaveSlot("save", "saved", 0, 0, state))
            val loaded = PlayViewModel("s", "save", library, director) { null }
            runCurrent()
            assertEquals(state.history, loaded.ui.value.session!!.history)
            loaded.chooseAuthored(1)
            assertEquals("结局标题", loaded.ui.value.nodeTitle)
            assertTrue(loaded.ui.value.visibleChoices.isEmpty())
            assertTrue(loaded.ui.value.pendingAiChoices.isEmpty())
            assertFalse(loaded.ui.value.aiTargetExit)
            assertEquals(1, loaded.ui.value.session!!.history.count { it.text == "离开" })
            assertEquals(1, loaded.ui.value.session!!.history.count { it.kind == EntryKind.SYSTEM })
            val ending = loaded.ui.value.session!!
            library.upsertSave(SaveSlot("end", "ending", 0, 0, ending))
            val end = PlayViewModel("s", "end", library, director) { null }
            runCurrent()
            assertEquals(ending.history, end.ui.value.session!!.history)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun aiChoicesSurviveSaveAndLoadWithoutRegeneration() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val library = LocalLibrary(temp.newFolder())
            val story = Story("ai", "AI story", nodes = mapOf(
                "start" to StoryNode("start", NodeKind.AI, title = "AI node")
            ))
            library.upsertStory(story)
            val choices = listOf(ChoiceData("推门", "hall"), ChoiceData("等待", "@self"))
            val state = GameEngine.newSession(story).copy(
                history = listOf(LogEntry(EntryKind.NARRATION, text = "门出现了")),
                pendingAiChoices = choices,
                aiAwaitingChoice = true
            )
            library.upsertSave(SaveSlot("save-ai", "saved", 0, 0, state))
            val vm = PlayViewModel("ai", "save-ai", library, AiDirector(ChatClient())) { null }
            runCurrent()
            assertEquals(PlayStage.AUTHORED, vm.ui.value.stage)
            assertEquals(choices.map { it.text }, vm.ui.value.pendingAiChoices.map { it.text })
            assertEquals(state.history, vm.ui.value.session!!.history)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun consecutiveAiNodesGenerateWithoutGettingStuck() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val scene = if (requests.incrementAndGet() == 1)
                """{"text":"第一幕","choices":[]}"""
            else """{"text":"第二幕","choices":[{"text":"留下"}]}"""
            val body = AppJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(),
                kotlinx.serialization.json.buildJsonObject {
                    put("choices", kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.buildJsonObject {
                            put("message", kotlinx.serialization.json.buildJsonObject {
                                put("content", kotlinx.serialization.json.JsonPrimitive(scene))
                            })
                        })
                    })
                })
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        try {
            val library = LocalLibrary(temp.newFolder())
            library.upsertProvider(ApiProfile("p", "test", baseUrl = "http://localhost/v1", model = "test"))
            library.upsertStory(Story("chain", "连续 AI", nodes = mapOf(
                "start" to StoryNode("start", NodeKind.AI, endTarget = "next"),
                "next" to StoryNode("next", NodeKind.AI)
            )))
            val vm = PlayViewModel("chain", "new", library, AiDirector(ChatClient(client))) { "p" }
            vm.selectPlayerCharacter("")
            val ready = withTimeout(10_000) {
                vm.ui.first { it.stage == PlayStage.AUTHORED && it.nodeId == "next" }
            }
            assertEquals(2, requests.get())
            assertEquals(2, ready.session!!.aiTurns)
            withTimeout(5_000) { library.achievements.first { records -> records.any { it.id == "FIRST_AI" && it.unlockedAt > 0L } } }
            assertEquals(listOf("第一幕", "第二幕"), ready.session!!.history.map { it.text })
            assertEquals("留下", ready.pendingAiChoices.single().text)
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            Dispatchers.resetMain()
        }
    }

    @Test fun failedWritesKeepMemoryAndExistingData() = runBlocking {
        val dir = temp.newFolder()
        val library = LocalLibrary(dir)
        val character = CharacterData("c", "original")
        library.upsertCharacter(character)
        val file = File(dir, "characters.json")
        val before = file.readText()
        try {
            library.upsertCharacter(character.copy(initial = CharacterState(metrics = mapOf("bad" to Double.NaN))))
            fail("Serialization failure must reach the caller")
        } catch (_: IOException) { }
        assertEquals(listOf(character), library.characters.value)
        assertEquals(before, file.readText())
        // A directory at the target path reliably simulates replacement failure, even as administrator.
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        File(file, "keep").writeText(before)
        try {
            library.upsertCharacter(character.copy(name = "lost"))
            fail("Write failure must reach the caller")
        } catch (_: IOException) { }
        assertEquals(listOf(character), library.characters.value)
        assertEquals(before, File(file, "keep").readText())
        assertNotNull(library.writeError.value)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".pending") })
    }

    @Test fun automaticNodeCyclesStopAndLargeDiceDoNotOverflow() = runTest {
        val roll = Effect(EffectType.ROLL, "die", to = Int.MAX_VALUE.toDouble())
        val state = GameEngine.applyEffects(SessionState("s"), listOf(roll, roll.copy(charId = "c"))).state
        assertTrue(state.variables.getValue("die") in 1.0..Int.MAX_VALUE.toDouble())
        assertTrue(state.characterStates.getValue("c").metrics.getValue("die") in 0.0..100.0)
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val library = LocalLibrary(temp.newFolder())
            library.upsertStory(Story("cycle", "cycle", nodes = mapOf(
                "start" to StoryNode("start", text = "A", endTarget = "b"),
                "b" to StoryNode("b", text = "B", endTarget = "start")
            )))
            val vm = PlayViewModel("cycle", "new", library, AiDirector(ChatClient())) { null }
            runCurrent()
            vm.selectPlayerCharacter("")
            assertEquals(PlayStage.STOPPED, vm.ui.value.stage)
            assertEquals("剧情循环", vm.ui.value.stoppedTitle)
            assertEquals(2, vm.ui.value.session!!.history.size)
        } finally { Dispatchers.resetMain() }
    }
}
