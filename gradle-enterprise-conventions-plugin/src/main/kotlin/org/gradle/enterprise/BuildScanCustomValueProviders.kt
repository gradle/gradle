package org.gradle.enterprise

import com.gradle.scan.plugin.BuildScanExtension
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object CITagProvider : BuildScanCustomValueProvider {
    override fun enabled() = isCiServer
    override fun execute(buildScan: BuildScanExtension) = buildScan.tag("CI")
}

abstract class CICustomValueProvider(private val markEnvVariableName: String) : BuildScanCustomValueProvider {
    override fun enabled() = markEnvVariableName in System.getenv()
}

object TravisCICustomValueProvider : CICustomValueProvider("TRAVIS") {
    override fun execute(buildScan: BuildScanExtension) {
        buildScan.link("Travis Build", System.getenv("TRAVIS_BUILD_WEB_URL"))
        buildScan.value("Build ID", System.getenv("TRAVIS_BUILD_ID"))
        buildScan.setCommitId(System.getenv("TRAVIS_COMMIT"))
    }
}

object JenkinsCiCustomValueProvider : CICustomValueProvider("JENKINS_HOME") {
    override fun execute(buildScan: BuildScanExtension) {
        buildScan.link("Jenkins Build", System.getenv("BUILD_URL"))
        buildScan.value("Build ID", System.getenv("BUILD_ID"))
        buildScan.setCommitId(System.getenv("GIT_COMMIT"))
    }
}

object GitHubActionsCustomValueProvider : CICustomValueProvider("GITHUB_ACTIONS") {
    override fun execute(buildScan: BuildScanExtension) {
        val gitCommitName = "Git Commit ID"
        val commitId = System.getenv("GITHUB_SHA")
        buildScan.value("Build ID", "${System.getenv("GITHUB_RUN_ID")} ${System.getenv("GITHUB_RUN_NUMBER")}")
        buildScan.value(gitCommitName, commitId)
        buildScan.link("Git Commit Scans", customValueSearchUrl(mapOf(gitCommitName to commitId)))
        buildScan.background {
            getRemoteGitHubRepository()?.also {
                buildScan.link("GitHub Actions Build", "${it}/runs/${System.getenv("GITHUB_RUN_ID")}")
                buildScan.link("Source", "${it}/commit/$commitId")
            }
        }
    }
}

object TeamCityCustomValueProvider : CICustomValueProvider("TEAMCITY_VERSION") {
    override fun execute(buildScan: BuildScanExtension) {
        buildScan.link("TeamCity Build", System.getenv("BUILD_URL"))
        buildScan.value("Build ID", System.getenv("BUILD_ID"))
        buildScan.setCommitId(System.getenv("BUILD_VCS_NUMBER"))
    }
}

object LocalBuildCustomValueProvider : BuildScanCustomValueProvider {
    override fun enabled() = !isCiServer
    override fun execute(buildScan: BuildScanExtension) {
        buildScan.tag("LOCAL")
        if (listOf("idea.registered", "idea.active", "idea.paths.selector").mapNotNull(System::getProperty).isNotEmpty()) {
            buildScan.tag("IDEA")
            System.getProperty("idea.paths.selector")?.let { ideaVersion ->
                buildScan.value("IDEA version", ideaVersion)
            }
        }
    }
}

object GitInformationCustomValueProvider : BuildScanCustomValueProvider {
    override fun execute(buildScan: BuildScanExtension) {
        buildScan.background {
            execAndGetStdoutOrNull("git", "status", "--porcelain")?.also {
                buildScan.tag("dirty")
                buildScan.value("Git Status", it)
            }

            execAndGetStdoutOrNull("git", "rev-parse", "--abbrev-ref", "HEAD")?.also {
                buildScan.value("Git Branch Name", it)
            }
        }
    }
}


fun BuildScanExtension.setCommitId(commitId: String) {
    val gitCommitName = "Git Commit ID"
    value(gitCommitName, commitId)
    background {
        getRemoteGitHubRepository()?.also {
            link("Source", "${it}/commit/$commitId")
        }
    }
    link("Git Commit Scans", customValueSearchUrl(mapOf(gitCommitName to commitId)))
}


private
fun customValueSearchUrl(search: Map<String, String>): String {
    val query = search.map { (name, value) ->
        "search.names=${name.urlEncode()}&search.values=${value.urlEncode()}"
    }.joinToString("&")

    return "$gradleEnterpriseServerUrl/scans?$query"
}

private
fun String.urlEncode() = URLEncoder.encode(this, Charsets.UTF_8.name())

private
fun execAndGetStdoutOrNull(vararg args: String): String? {
    return try {
        val process = ProcessBuilder(*args).start()
        process.waitFor(1, TimeUnit.MINUTES)
        process.inputStream.bufferedReader().readText()
    } catch (e: IOException) {
        null
    }
}

private fun getRemoteGitHubRepository(): String? {
    return execAndGetStdoutOrNull("git", "config", "--get", "remote.origin.url")?.parseGitHubRemoteUrl()
}

val sshUrlPattern = """git@github\.com:([\w-]+)/([\w-]+)\.git""".toRegex()
val httpsUrlPattern = """https://github\.com/([\w-]+)/([\w-]+)\.git""".toRegex()

/*
 Parses GitHub url and returns the repository
 */
private fun String.parseGitHubRemoteUrl(): String? {
    val nameAndRepo = when {
        this.matches(sshUrlPattern) -> sshUrlPattern.find(this)!!.groupValues
        this.matches(httpsUrlPattern) -> httpsUrlPattern.find(this)!!.groupValues
        else -> null
    }
    return nameAndRepo?.let { "https://github.com/${it[0]}/${it[1]}" }
}


