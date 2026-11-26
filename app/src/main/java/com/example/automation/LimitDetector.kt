package com.example.automation

import java.text.Normalizer

data class LimitDetectionResult(
    val detected: Boolean,
    val matchedPhrase: String? = null,
    val rawSnippet: String? = null,
)

object LimitDetector {

    private val SECONDARY_CONFIRMATION_CONTEXT = listOf(
        "usage limit",
        "session limit",
        "reached your limit",
        "reached your usage limit",
        "limite de uso",
        "limite de sesion",
        "alcanzado tu limite",
        "alcanzaste el limite",
        "llegado al limite",
        "try again",
        "come back",
        "vuelve a intentarlo",
        "intenta mas tarde",
        "upgrade",
        "reset",
        "se restablece",
        "available again",
    )

    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents
            .replace("’", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun detect(
        visibleTexts: List<String>,
        configuredPhrases: List<String>,
        sentResumePrompt: String,
    ): LimitDetectionResult {
        if (visibleTexts.isEmpty() || configuredPhrases.isEmpty()) {
            return LimitDetectionResult(detected = false)
        }

        val normalizedPrompt = normalize(sentResumePrompt)
        val promptSignature = normalizedPrompt.take(48)
        val normalizedPhrases = configuredPhrases
            .map { it.trim() to normalize(it) }
            .filter { it.second.length >= 8 }

        for (rawText in visibleTexts) {
            val trimmed = rawText.trim()
            if (trimmed.length < 8) continue

            val normText = normalize(trimmed)

            // Evitar falsos positivos si el texto visible es nuestra propia instrucción enviada
            if (promptSignature.isNotBlank() && normText.contains(promptSignature)) {
                continue
            }
            if (normalizedPrompt.isNotBlank() && normalizedPrompt.contains(normText) && normText.length > 24) {
                continue
            }

            // Ignorar bloques de texto muy largos de conversación normal a menos que contengan
            // una frase de sistema completa y específica (las notificaciones de límite suelen ser breves)
            val isLongChatParagraph = normText.length > 260

            for ((originalPhrase, normPhrase) in normalizedPhrases) {
                if (!normText.contains(normPhrase)) continue

                // Si es un párrafo muy largo de respuesta normal, exigir frase de alta especificidad
                if (isLongChatParagraph && !isHighConfidenceSystemPhrase(normPhrase)) {
                    continue
                }

                // Para frases genéricas como "try again later" o "come back later",
                // aceptar si el bloque es corto (<180 chars) o tiene contexto de límite/sesión.
                if (isAmbiguousRetryPhrase(normPhrase) && normText.length > 180) {
                    val hasLimitContext = SECONDARY_CONFIRMATION_CONTEXT.any { normText.contains(it) }
                    if (!hasLimitContext) continue
                }

                return LimitDetectionResult(
                    detected = true,
                    matchedPhrase = originalPhrase,
                    rawSnippet = trimmed.take(180),
                )
            }
        }

        return LimitDetectionResult(detected = false)
    }

    private fun isHighConfidenceSystemPhrase(normPhrase: String): Boolean {
        return normPhrase.contains("reached your") ||
            normPhrase.contains("limit reached") ||
            normPhrase.contains("alcanzado tu limite") ||
            normPhrase.contains("alcanzaste el limite") ||
            normPhrase.contains("limite de sesion") ||
            normPhrase.contains("llegado al limite")
    }

    private fun isAmbiguousRetryPhrase(normPhrase: String): Boolean {
        return normPhrase == "try again later" ||
            normPhrase == "come back later" ||
            normPhrase == "vuelve a intentarlo mas tarde"
    }
}
