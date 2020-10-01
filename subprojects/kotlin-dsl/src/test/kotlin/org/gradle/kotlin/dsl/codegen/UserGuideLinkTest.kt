package org.gradle.kotlin.dsl.codegen

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.net.HttpURLConnection
import java.net.URL


class UserGuideLinkTest {

    @Test
    fun `linked plugins`() {
        linkedPlugins.forEach {
            assertThat(
                "$it is linked",
                UserGuideLink.forPlugin(it),
                notNullValue()
            )
        }
    }

    @Test
    fun `unlinked plugins`() {
        unlinkedPlugins.forEach {
            assertThat(
                "$it is not linked",
                UserGuideLink.forPlugin(it),
                nullValue()
            )
        }
    }
}


@RunWith(Parameterized::class)
class UserGuideLinkIntegrationTest(
    private val pluginId: String
) {

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun testCases() = linkedPlugins
    }

    @Test
    fun `linked resource is available`() {
        val link = UserGuideLink.forPlugin(pluginId)!!
        val url = URL(link.replace("/current/", "/nightly/"))
        (url.openConnection() as HttpURLConnection).run {
            requestMethod = "HEAD"
            assertThat(url.toString(), responseCode, equalTo(200))
        }
    }
}


val linkedPlugins =
    listOf(
        "antlr", "application", "assembler", "assembler-lang",
        "base", "binary-base", "build-dashboard", "build-init",
        "c", "c-lang", "checkstyle", "clang-compiler", "codenarc",
        "cpp", "cpp-executable", "cpp-lang", "cpp-library",
        "component-base", "component-model-base",
        "cunit", "cunit-test-suite",
        "distribution",
        "ear", "eclipse", "eclipse-wtp",
        "gcc-compiler", "google-test", "google-test-test-suite",
        "groovy", "groovy-base",
        "help-tasks",
        "idea", "ivy-publish",
        "jacoco", "java", "java-base", "java-gradle-plugin",
        "java-library", "java-library-distribution",
        "maven", "maven-publish",
        "microsoft-visual-cpp-compiler",
        "native-component", "native-component-model",
        "objective-c", "objective-c-lang", "objective-cpp", "objective-cpp-lang",
        "play", "play-application", "play-cofeescript", "play-ide", "play-javascript", "pmd",
        "project-report", "project-reports",
        "reporting-base",
        "scala", "scala-base", "signing", "standard-tool-chains",
        "visual-studio",
        "war",
        "windows-resource-script", "windows-resources"
    )


val unlinkedPlugins =
    listOf(
        "coffeescript-base", "envjs", "javascript-base", "jshint",
        "java-lang", "junit-test-suite", "jvm-component", "jvm-resources",
        "language-base", "lifecycle-base",
        "publishing",
        "rhino",
        "scala-lang",
        "wrapper"
    )
