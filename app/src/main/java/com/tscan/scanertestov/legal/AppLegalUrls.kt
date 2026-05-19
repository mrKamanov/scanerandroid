package com.tscan.scanertestov.legal

/**
 * Описание: публичные URL документов на GitHub (политика, исходники, лицензия).
 *
 * Должен совпадать с реальным репозиторием: https://github.com/ВАШ_ЛОГИН/scanerandroid
 * Тот же URL политики — в Google Play Console.
 */
object AppLegalUrls {
    /** Без завершающего `/`. */
    const val GITHUB_REPOSITORY = "https://github.com/mrKamanov/scanerandroid"

    private const val DEFAULT_BRANCH = "main"

    /** Для Google Play и кнопки в настройках. */
    val privacyPolicy: String
        get() = "$GITHUB_REPOSITORY/blob/$DEFAULT_BRANCH/PRIVACY.md"

    val sourceCode: String
        get() = GITHUB_REPOSITORY

    val license: String
        get() = "$GITHUB_REPOSITORY/blob/$DEFAULT_BRANCH/LICENSE"
}
