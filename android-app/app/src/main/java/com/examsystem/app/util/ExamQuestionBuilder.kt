package com.examsystem.app.util

import com.examsystem.app.data.models.Test
import com.examsystem.app.ui.screens.Option
import com.examsystem.app.ui.screens.Question
import kotlin.random.Random

object ExamQuestionBuilder {
    /**
     * Builds exam UI questions. Option labels (A–D) stay tied to the correct answer key;
     * only display order is shuffled when enabled.
     */
    fun build(test: Test, seed: Long = System.currentTimeMillis()): List<Question> {
        val config = AntiCheatConfig.fromTest(test)
        val rng = Random(seed)
        val all = test.sections.orEmpty()
            .flatMap { it.questions.orEmpty() }
            .filter { it.id.isNotBlank() }
        val ordered = if (config.randomizeQuestions) all.shuffled(rng) else all
        return ordered.map { q ->
            val opts = listOf(
                Option("A", q.optionA.orEmpty()),
                Option("B", q.optionB.orEmpty()),
                Option("C", q.optionC.orEmpty()),
                Option("D", q.optionD.orEmpty())
            )
            val displayOpts = if (config.randomizeOptions) opts.shuffled(rng) else opts
            Question(
                id = q.id,
                text = q.text.orEmpty(),
                imageUrl = q.imageUrl.orEmpty(),
                options = displayOpts
            )
        }
    }
}
