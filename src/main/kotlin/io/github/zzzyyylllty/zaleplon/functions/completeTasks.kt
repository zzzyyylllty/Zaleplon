package io.github.zzzyyylllty.zaleplon.functions

import io.github.zzzyyylllty.zaleplon.Zaleplon.config
import io.github.zzzyyylllty.zaleplon.Zaleplon.questsMap
import io.github.zzzyyylllty.zaleplon.data.QuestStat
import org.bukkit.entity.Player
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.common.util.asList
import taboolib.platform.util.asLangText

fun completeTasks(p: Player?, objective: String, amount: Number, metaList: LinkedHashMap<String, Any?>) {

    if (p == null) return

    submitAsync {

        val data = questsMap[p]

        if (data == null) { // If Player data not found
            when (config["debug.no-player-data"]) {
                "SKIP" -> warning(p.asLangText("DEBUG_NO_PLAYER_DATA_WARN", p.name))
                "KICK" -> run {
                    warning(p.asLangText("DEBUG_NO_PLAYER_DATA_WARN", p.name))
                    p.kickPlayer(p.asLangText("DEBUG_NO_PLAYER_DATA_WARN_KICK", p.name))
                }
            }
            return@submitAsync
        }

        for (rawquest in data.quests) {

            val questId = rawquest.key
            val quest = rawquest.value

            for (rawtask in quest.questTasks) {

                val taskKey = rawtask.key
                val task = rawtask.value

                // If it already completes
                if (task.goal.toDouble() >= task.progress.toDouble()) break

                val dependency = task.task.addon?.dependency
                if (dependency?.isNotEmpty() == true) {
                    for (depend in dependency) {
                        var completedDepends = 0
                        if (depend.quests == null) continue

                        for (q in depend.quests) {

                            val sp = q.split(".")
                            if (questsMap[p] != null && questsMap[p]?.quests?.keys?.contains(sp[0]) == true) {

                                // xxx.all 代表整个任务，检测是否整个任务完成
                                if (sp[1] == "all") if (questsMap[p]?.quests?.get(sp[0])?.questStat == QuestStat.COMPLETED) completedDepends++

                                // 否则
                                else {
                                    val progress = questsMap[p]?.quests?.get(sp[0])?.questTasks?.get(sp[1])?.progress ?: 0
                                    val goal = questsMap[p]?.quests?.get(sp[0])?.questTasks?.get(sp[1])?.goal ?: 0
                                    if (goal.toInt() <= progress.toInt()) completedDepends++
                                }
                            }
                        }
                    }
                }

                for (rawObjective in task.task.taskObjectives)
                    for (meta in rawObjective.meta) {

                        // META
                        var isTaskMetaTrue = false
                        if (meta.value is String) isTaskMetaTrue = compare(
                            meta.value.toString(),
                            metaList[meta.key].toString(),
                            p.asLangText(
                                "DEBUG_META_COMPARE_ERROR",
                                p.name,
                                meta.value,
                                metaList[meta.key] ?: "(null)",
                                questId,
                                taskKey
                            )
                        )
                        if (meta.value !is String) isTaskMetaTrue = compare(
                            meta.value as Double,
                            metaList[meta.key].toString(),
                            p.asLangText(
                                "DEBUG_META_COMPARE_ERROR",
                                p.name,
                                meta.value,
                                metaList[meta.key] ?: "(null)",
                                questId,
                                taskKey
                            )
                        )
                        if (!isTaskMetaTrue) {
                            task.addon.agent?.onProgressFail?.asList()?.let { runKether(it, p) }
                            break
                        }

                        // Kether Requirement
                        if (rawObjective.requirement != null && runKether(
                                listOf(rawObjective.requirement),
                                p
                            ).get() as Boolean? == false
                        ) {
                            task.addon.agent?.onProgressFail?.asList()?.let { runKether(it, p) }
                            break
                        }

                        // Progress added
                        task.addon.agent?.onProgress?.asList()?.let { runKether(it, p) }

                        // Kether run
                        if (rawObjective.run != null) runKether(listOf(rawObjective.run), p).get()
                    }
            }
        }
    }
}
