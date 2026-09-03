package com.tananaev.passportreader

/**
 * §6.2 item 24 (D70(c)) — the visible version stamp: `versionName` + the
 * short git sha of the commit the running build was compiled from. Pure
 * and Android-free by construction — [format] takes both values as
 * arguments rather than reading `BuildConfig` itself, so it is the ONE
 * place this string's shape is decided and is testable with plain literal
 * expectations, with no Gradle/BuildConfig plumbing anywhere near the test.
 *
 * Two callers, both outside this class: the scan-pane footer TextView
 * ([MainActivity.onCreate]) and each log entry's technical line
 * ([ReportLog]'s `renderEntry`) — both pass `BuildConfig.VERSION_NAME` /
 * `BuildConfig.GIT_SHA` so there is exactly one place (`build.gradle.kts`)
 * that computes the sha, never a second, independently-derived source.
 */
object VersionStamp {
    /** @return `"v$versionName ($sha)"`, e.g. `"v0.2.0 (abc1234)"`. Neither
     *  argument is validated or reformatted — whatever the caller passes
     *  (including `BuildConfig`'s "nogit" / "-dirty" fallback values) is
     *  shown verbatim. */
    fun format(versionName: String, sha: String): String = "v$versionName ($sha)"
}
