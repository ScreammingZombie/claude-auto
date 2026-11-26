package com.example.automation

sealed class AuthAndCaptchaResult {
    data object Clear : AuthAndCaptchaResult()
    data class InterventionRequired(val reason: String) : AuthAndCaptchaResult()
}

object AuthAndCaptchaDetector {

    private val CAPTCHA_INDICATORS = listOf(
        "verify you are human",
        "checking your browser",
        "just a moment",
        "cloudflare",
        "captcha",
        "security check",
        "verificacion de seguridad",
        "comprobando si eres humano",
        "verifica que eres humano",
        "completing the captcha",
    )

    private val LOGIN_INDICATORS = listOf(
        "continue with google",
        "continue with email",
        "continuar con google",
        "continuar con correo",
        "enter your email",
        "ingresa tu correo",
        "sign in to claude",
        "iniciar sesion en claude",
        "log in to claude",
        "get started with claude",
        "welcome to claude",
    )

    fun evaluate(
        visibleTexts: List<String>,
        hasMessageComposer: Boolean,
    ): AuthAndCaptchaResult {
        val normalizedTexts = visibleTexts.map { LimitDetector.normalize(it) }

        val captchaHit = normalizedTexts.firstOrNull { text ->
            CAPTCHA_INDICATORS.any { indicator -> text.contains(indicator) }
        }
        if (captchaHit != null && !hasMessageComposer) {
            return AuthAndCaptchaResult.InterventionRequired(
                "Se detectó verificación de seguridad o CAPTCHA ($captchaHit). Complétalo manualmente.",
            )
        }

        if (!hasMessageComposer) {
            val loginHit = normalizedTexts.firstOrNull { text ->
                LOGIN_INDICATORS.any { indicator -> text.contains(indicator) }
            }
            if (loginHit != null) {
                return AuthAndCaptchaResult.InterventionRequired(
                    "Sesión de Claude cerrada o pantalla de inicio de sesión detectada ($loginHit). Inicia sesión manualmente en tu navegador.",
                )
            }
        }

        return AuthAndCaptchaResult.Clear
    }
}
