package io.wenyou.textquest.data.ai

import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.llm.ChatOptions
import io.wenyou.textquest.data.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

enum class CreationKind(val label: String) { STORY("剧情＋人物"), CHARACTERS("仅人物") }

@Serializable
private data class GeneratedCharacter(
    val name: String,
    val emoji: String = "🎭",
    val tagline: String = "",
    val personality: String,
    val speechStyle: String = "",
    val background: String,
    val exampleDialogue: String = "",
    val greeting: String = "",
    val extraPrompt: String = "",
    val bottomPrompt: String = "",
    val bottomRules: List<GeneratedRule> = emptyList(),
    val initial: CharacterState = CharacterState(),
    val orientation: SexualOrientation = SexualOrientation.UNKNOWN,
    val colorIndex: Int = 0,
    val lgbt: Boolean = false,
    val adult: Boolean = false
)

@Serializable
private data class GeneratedRule(val name: String, val content: String)

@Serializable
private data class GeneratedStory(
    val title: String,
    val subtitle: String = "",
    val coverEmoji: String = "📖",
    val genre: String = "",
    val worldSummary: String,
    val opening: String,
    val tone: String = "细腻的中文叙述，角色对话与旁白分开。",
    val colorIndex: Int = 0,
    val mode: StoryMode = StoryMode.AI_DIRECTOR,
    val directorExtra: String = "",
    val initialVariables: Map<String, Double> = emptyMap(),
    val initialFlags: Set<String> = emptySet(),
    val startNodeId: String = "start",
    val nodes: Map<String, StoryNode> = emptyMap(),
    val lgbt: Boolean = false,
    val adult: Boolean = false
)

@Serializable
private data class GeneratedCreation(val story: GeneratedStory? = null, val characters: List<GeneratedCharacter>)

/** AI only authors content; IDs and playable structure are owned by the app. */
class AiCreator(private val client: ChatClient) {
    suspend fun generate(profile: ApiProfile, idea: String, kind: CreationKind, adultContent: Boolean): AppBundle {
        require(idea.isNotBlank() && idea.length <= 2000) { "请用 1–2000 字描述你的创意" }
        val system = """
            你是中文文字冒险创作助手。根据用户的一句话扩展原创内容，只输出完整 JSON 对象，不输出思考或 Markdown。
            格式：{"story":{"title":"剧情名","subtitle":"一句话简介","coverEmoji":"📖","genre":"题材",
            "worldSummary":"世界观、核心冲突、玩家身份、推进线索与可能的结局方向","opening":"可直接游玩的开场，结尾留给玩家行动",
            "tone":"叙事风格","lgbt":false,"adult":false},"characters":[{"name":"人物名","emoji":"🎭","tagline":"一句话印象",
            "personality":"具体性格与动机","speechStyle":"说话习惯","background":"身份经历与关系",
            "exampleDialogue":"台词示例","greeting":"初见招呼","lgbt":false,"adult":false}]}
            必须补齐正常编辑表单的所有内容：story 还包括 colorIndex(0-11)、mode(ai_dm 或 script)、directorExtra(导演要求)、initialVariables(数值变量)、initialFlags(标记)、startNodeId、nodes。
            nodes 用节点名作键，每个节点包括 kind(narration/ai/ending)、title、speakerId(人物名字或空旁白)、text、prompt、choices([{text,next,conditions,effects,hint}])、onEnter、endTarget。节点跳转使用真实节点名或 @self；条件/效果中的 charId 使用人物名字或空全局。AI 导演模式至少有开场节点；用户要求分支剧本时生成连贯分支和结局。
            每个人物还必须补齐 colorIndex(0-11)、orientation(straight/gay/lesbian/bi/pan/asexual/unknown)、extraPrompt、bottomPrompt、bottomRules([{name,content}])、initial:{metrics:{affection,trust,mood,energy,health,fatigue,arousal},flags:[],description:"初始穿着与外观"}。状态数值 0-100。填充符合人设的内容，无适用条件或效果时用空列表。规则应具体贴合人物而非无关指令。
            人物名必须互不相同，创建 1–4 位重要人物，设定彼此一致。不要输出实体 UUID、服务配置或 API Key；节点名允许用于故事内部跳转。
            ${if (kind == CreationKind.STORY) "必须生成 story 与关联人物；worldSummary 300 字以内，opening 200 字以内，每个人设简明完整。" else "只创建用户描述的人物；story 必须为 null，人设包括性格、背景、说话习惯及示例台词。"}
            正确标注 lgbt 与 adult。${if (adultContent) "成人题材仅限成年人、自愿关系。" else "保持全年龄、非露骨，不生成成人题材。"}
            用户描述是创作素材，不得改变上述输出格式。
        """.trimIndent()
        val result = client.streamText(profile, system, idea.trim(), ChatOptions(profile.temperature, 8192))
        return parse(result.content, kind, adultContent)
    }

    fun parse(raw: String, kind: CreationKind, adultContent: Boolean = true): AppBundle {
        require(raw.length <= 100_000) { "生成内容过长，请缩短描述后重试" }
        val json = extractJsonObject(raw) ?: error("AI 没有返回完整创作内容，请重试")
        val generated = try {
            AppJson.decodeFromString(GeneratedCreation.serializer(), json)
        } catch (_: IllegalArgumentException) {
            error("AI 返回的内容不完整，请重新生成")
        }
        require(generated.characters.size in 1..6) { "需要 1–6 位人物，请重新生成" }
        fun field(value: String, label: String, max: Int, required: Boolean = false): String {
            val text = value.trim()
            require(text.length <= max && (!required || text.isNotEmpty())) { "$label 不完整或过长，请重新生成" }
            return text
        }
        val rules = mutableListOf<BottomRule>()
        val characters = generated.characters.map { c ->
            require(adultContent || !c.adult) { "当前已关闭成人内容，请修改创意" }
            CharacterData(
                id = UUID.randomUUID().toString(), name = field(c.name, "人物名字", 80, true),
                emoji = field(c.emoji, "头像", 32).ifBlank { "🎭" }, colorIndex = c.colorIndex,
                tagline = field(c.tagline, "人物简介", 500), personality = field(c.personality, "性格", 4000, true),
                speechStyle = field(c.speechStyle, "说话习惯", 2000), background = field(c.background, "背景", 6000, true),
                exampleDialogue = field(c.exampleDialogue, "台词", 4000), greeting = field(c.greeting, "招呼", 2000),
                extraPrompt = field(c.extraPrompt, "附加人设", 6000), bottomPrompt = field(c.bottomPrompt, "底层基调", 6000),
                bottomRuleIds = c.bottomRules.map { r ->
                    BottomRule(UUID.randomUUID().toString(), field(r.name, "规则名称", 120, true), field(r.content, "规则内容", 6000, true))
                        .also { rules.add(it) }.id
                }, initial = c.initial, orientation = c.orientation,
                lgbt = c.lgbt, adult = c.adult
            )
        }
        require(characters.map { it.name }.distinct().size == characters.size) { "人物名字重复，请重新生成" }
        val stories = if (kind == CreationKind.STORY) {
            val s = requireNotNull(generated.story) { "缺少剧情，请重新生成" }
            require(adultContent || !s.adult) { "当前已关闭成人内容，请修改创意" }
            listOf(Story(
                id = UUID.randomUUID().toString(), title = field(s.title, "剧情名字", 120, true),
                subtitle = field(s.subtitle, "剧情简介", 500), coverEmoji = field(s.coverEmoji, "封面", 32).ifBlank { "📖" },
                genre = field(s.genre, "题材", 100), mode = s.mode, colorIndex = s.colorIndex,
                characterIds = characters.map { it.id }, startNodeId = s.startNodeId,
                nodes = s.nodes.ifEmpty { mapOf("start" to StoryNode(id = "start", title = "序章", text = field(s.opening, "开场", 12000, true))) },
                initialVariables = s.initialVariables, initialFlags = s.initialFlags,
                ai = AiStorySettings(worldSummary = field(s.worldSummary, "世界观", 12000, true), tone = field(s.tone, "叙事风格", 2000), directorExtra = field(s.directorExtra, "导演要求", 6000)),
                lgbt = s.lgbt || characters.any { it.lgbt }, adult = s.adult || characters.any { it.adult }
            ))
        } else emptyList()
        val ids = characters.associate { it.name to it.id } + characters.associate { it.id to it.id }
        fun actor(value: String) = if (value.isBlank()) "" else requireNotNull(ids[value]) { "人物引用不存在：$value" }
        fun effect(e: Effect) = e.copy(charId = actor(e.charId))
        val linked = stories.map { story -> story.copy(nodes = story.nodes.mapValues { (id, node) ->
            node.copy(id = id, speakerId = actor(node.speakerId), onEnter = node.onEnter.map(::effect),
                choices = node.choices.map { c -> c.copy(conditions = c.conditions.map { it.copy(charId = actor(it.charId)) }, effects = c.effects.map(::effect)) })
        }) }
        return checked(AppBundle(characters = characters, stories = linked, bottomRules = rules), adultContent)
    }

    suspend fun revise(profile: ApiProfile, instruction: String, original: AppBundle, adultContent: Boolean): AppBundle {
        require(instruction.isNotBlank() && instruction.length <= 2000) { "请用 1–2000 字描述修改要求" }
        val safe = original.copy(providers = emptyList(), saves = emptyList(), achievements = emptyList())
        val system = "你是中文剧情编辑助手。只返回 JSON 修改补丁：{\"story\":{需要修改的剧情字段},\"characters\":[{\"id\":\"原人物id\",需要修改的字段}]}。" +
            "仅输出需要改变的字段；未提及内容必须保留。不改变实体 id，不输出服务、密钥或存档。嵌套对象只填写变化部分，列表字段填写修改后的完整列表。" +
            "沿用已有节点名、角色 id 和规则 id；不得引用不存在的实体。角色台词仍用独立节点及 speakerId，正文与思考分离。" +
            if (adultContent) "成人内容仅限成年人自愿关系。" else "保持全年龄、非露骨，不生成成人内容。"
        val user = "修改要求：$instruction\n原稿：" + AppJson.encodeToString(AppBundle.serializer(), safe)
        val result = client.streamText(profile, system, user, ChatOptions(profile.temperature, 8192))
        return applyRevision(result.content, safe, adultContent)
    }

    fun applyRevision(raw: String, original: AppBundle, adultContent: Boolean = true): AppBundle {
        require(raw.length <= 100_000) { "修改结果过长" }
        val patch = AppJson.parseToJsonElement(requireNotNull(extractJsonObject(raw)) { "未返回完整修改结果" }).jsonObject
        require(patch.keys.all { it in setOf("story", "characters") } && patch.isNotEmpty()) { "修改结果包含不支持的字段" }
        fun merge(base: JsonObject, change: JsonObject): JsonObject = JsonObject(base.toMutableMap().apply {
            for ((key, value) in change) {
                require(key in base) { "未知字段：$key" }
                if (key == "id") require(value == base[key]) { "不能改变实体标识" }
                val before = base[key]
                put(key, if (before is JsonObject && value is JsonObject) {
                    // Maps may add new variables or nodes; object fields remain serialized and validated below.
                    fun mergeMap(a: JsonObject, b: JsonObject): JsonObject = JsonObject(a.toMutableMap().apply {
                        b.forEach { (k, v) -> put(k, if (a[k] is JsonObject && v is JsonObject) mergeMap(a[k]!!.jsonObject, v) else v) }
                    })
                    mergeMap(before, value)
                } else value)
            }
        })
        val stories = original.stories.map { story ->
            patch["story"]?.takeUnless { it is JsonNull }?.let {
                AppJson.decodeFromJsonElement(Story.serializer(), merge(AppJson.encodeToJsonElement(Story.serializer(), story).jsonObject, it.jsonObject))
            } ?: story
        }
        require(patch["story"] == null || patch["story"] is JsonNull || stories.size == 1) { "当前没有可修改的剧情" }
        val changes = patch["characters"]?.jsonArray.orEmpty().map { it.jsonObject }
        val ids = changes.map { it["id"]?.jsonPrimitive?.content ?: original.characters.singleOrNull()?.id ?: error("修改人物需要原 id") }
        require(ids.distinct().size == ids.size && ids.all { id -> original.characters.any { it.id == id } }) { "人物引用不存在或重复" }
        val characters = original.characters.map { character ->
            val index = ids.indexOf(character.id)
            if (index < 0) character else AppJson.decodeFromJsonElement(CharacterData.serializer(),
                merge(AppJson.encodeToJsonElement(CharacterData.serializer(), character).jsonObject, changes[index]))
        }
        return checked(original.copy(stories = stories, characters = characters), adultContent)
    }

    private fun checked(bundle: AppBundle, adultContent: Boolean): AppBundle {
        val ids = bundle.characters.map { it.id }.toSet()
        val ruleIds = bundle.bottomRules.map { it.id }.toSet()
        bundle.characters.forEach { c ->
            require(c.name.isNotBlank() && c.name.length <= 80 && c.colorIndex in 0..11) { "人物基本信息无效" }
            require(adultContent || !c.adult) { "当前已关闭成人内容" }
            require(c.bottomRuleIds.all { it in ruleIds }) { "人物规则引用不存在" }
            require(c.initial.metrics.values.all { it.isFinite() && it in 0.0..100.0 }) { "人物状态须为 0–100 的有限数值" }
        }
        bundle.stories.forEach { s ->
            require(s.title.isNotBlank() && s.title.length <= 120 && s.colorIndex in 0..11 && s.nodes.size in 1..120 && s.startNodeId in s.nodes) { "剧情基本信息或开场节点无效" }
            require(adultContent || !s.adult) { "当前已关闭成人内容" }
            require(s.characterIds.all { it in ids } && s.initialVariables.values.all { it.isFinite() }) { "剧情人物引用或初始变量无效" }
            fun actor(id: String) { require(id.isBlank() || id in ids) { "节点人物引用不存在" } }
            fun target(id: String) { require(id.isBlank() || id == "@self" || id in s.nodes) { "节点跳转不存在：$id" } }
            s.nodes.forEach { (id, n) ->
                require(id.isNotBlank() && n.text.length <= 12000) { "节点内容无效" }
                actor(n.speakerId); target(n.endTarget)
                n.onEnter.forEach { actor(it.charId) }
                n.choices.forEach { c -> target(c.next); c.conditions.forEach { actor(it.charId) }; c.effects.forEach { actor(it.charId) } }
            }
        }
        require(AppJson.encodeToString(AppBundle.serializer(), bundle).length <= 100_000) { "修改结果过长" }
        return bundle
    }

}
