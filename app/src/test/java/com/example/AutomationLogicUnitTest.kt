package com.example

import com.example.automation.AuthAndCaptchaDetector
import com.example.automation.AuthAndCaptchaResult
import com.example.automation.ClaudeConversationMatcher
import com.example.automation.ConversationCandidate
import com.example.automation.ConversationMatchResult
import com.example.automation.LimitDetector
import com.example.model.DefaultAutomationText
import com.example.scheduler.ScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AutomationLogicUnitTest {

    @Test
    fun `schedule calculator picks today when target time is in future`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 25, 7, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = ScheduleCalculator.calculateNextExecutionTimeMillis(
            nowMillis = nowCal.timeInMillis,
            hour = 9,
            minute = 15,
            selectedDays = setOf(Calendar.WEDNESDAY, Calendar.FRIDAY),
        )
        assertNotNull(next)
        val resultCal = Calendar.getInstance().apply { timeInMillis = next!! }
        assertEquals(Calendar.WEDNESDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(9, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun `schedule calculator advances to next selected day when today time passed`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 25, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = ScheduleCalculator.calculateNextExecutionTimeMillis(
            nowMillis = nowCal.timeInMillis,
            hour = 8,
            minute = 0,
            selectedDays = setOf(Calendar.WEDNESDAY, Calendar.FRIDAY),
        )
        assertNotNull(next)
        val resultCal = Calendar.getInstance().apply { timeInMillis = next!! }
        assertEquals(Calendar.FRIDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, resultCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `schedule calculator returns null when no days selected`() {
        val next = ScheduleCalculator.calculateNextExecutionTimeMillis(
            nowMillis = System.currentTimeMillis(),
            hour = 8,
            minute = 0,
            selectedDays = emptySet(),
        )
        assertNull(next)
    }

    @Test
    fun `limit detector detects english and spanish limit notices`() {
        val resultEn = LimitDetector.detect(
            visibleTexts = listOf("You've reached your usage limit until 14:00."),
            configuredPhrases = DefaultAutomationText.DEFAULT_LIMIT_PHRASES,
            sentResumePrompt = DefaultAutomationText.DEFAULT_RESUME_PROMPT,
        )
        assertTrue(resultEn.detected)

        val resultEs = LimitDetector.detect(
            visibleTexts = listOf("Has alcanzado tu límite de uso de sesión."),
            configuredPhrases = DefaultAutomationText.DEFAULT_LIMIT_PHRASES,
            sentResumePrompt = DefaultAutomationText.DEFAULT_RESUME_PROMPT,
        )
        assertTrue(resultEs.detected)
    }

    @Test
    fun `limit detector ignores echoed prompt and casual mentions of limite`() {
        val resultEcho = LimitDetector.detect(
            visibleTexts = listOf(DefaultAutomationText.DEFAULT_RESUME_PROMPT),
            configuredPhrases = DefaultAutomationText.DEFAULT_LIMIT_PHRASES,
            sentResumePrompt = DefaultAutomationText.DEFAULT_RESUME_PROMPT,
        )
        assertFalse(resultEcho.detected)

        val resultCasual = LimitDetector.detect(
            visibleTexts = listOf(
                "El límite de la integral tiende a cero cuando x crece.",
                "Puedes revisar el límite máximo del array en Kotlin.",
            ),
            configuredPhrases = DefaultAutomationText.DEFAULT_LIMIT_PHRASES,
            sentResumePrompt = DefaultAutomationText.DEFAULT_RESUME_PROMPT,
        )
        assertFalse(resultCasual.detected)
    }

    @Test
    fun `conversation matcher resolves single match and flags ambiguous matches`() {
        val candidates = listOf(
            ConversationCandidate("Migración backend Kotlin", 1),
            ConversationCandidate("Refactorización UI Jetpack Compose", 2),
            ConversationCandidate("Informe mensual Q4", 3),
        )

        val single = ClaudeConversationMatcher.findMatch("Jetpack Compose", candidates)
        assertTrue(single is ConversationMatchResult.SingleMatch)
        assertEquals(2, (single as ConversationMatchResult.SingleMatch).candidate.payload)

        val ambiguousCandidates = listOf(
            ConversationCandidate("Proyecto Android parte 1", 10),
            ConversationCandidate("Proyecto Android parte 2", 11),
        )
        val ambiguous = ClaudeConversationMatcher.findMatch("Proyecto Android", ambiguousCandidates)
        assertTrue(ambiguous is ConversationMatchResult.AmbiguousMatch)
        assertEquals(2, (ambiguous as ConversationMatchResult.AmbiguousMatch).candidates.size)
    }

    @Test
    fun `auth and captcha detector requires intervention only when composer is absent`() {
        val captcha = AuthAndCaptchaDetector.evaluate(
            visibleTexts = listOf("Checking your browser", "Verify you are human"),
            hasMessageComposer = false,
        )
        assertTrue(captcha is AuthAndCaptchaResult.InterventionRequired)

        val loginScreen = AuthAndCaptchaDetector.evaluate(
            visibleTexts = listOf("Sign in to Claude", "Continue with Google"),
            hasMessageComposer = false,
        )
        assertTrue(loginScreen is AuthAndCaptchaResult.InterventionRequired)

        val activeChat = AuthAndCaptchaDetector.evaluate(
            visibleTexts = listOf("Aquí tienes la explicación sobre Cloudflare"),
            hasMessageComposer = true,
        )
        assertTrue(activeChat is AuthAndCaptchaResult.Clear)
    }
}
