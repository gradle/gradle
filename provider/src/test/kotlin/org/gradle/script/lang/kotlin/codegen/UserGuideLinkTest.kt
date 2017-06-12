package org.gradle.script.lang.kotlin.codegen

import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class UserGuideLinkTest {

    @Test
    fun `linked plugins`() {
        val linkedPlugins =
            listOf(
                "announce", "antlr", "application", "assembler", "assembler-lang",
                "base", "build-announcements", "build-dashboard", "build-init",
                "c", "c-lang", "checkstyle", "clang-compiler", "codenarc",
                "cpp", "cpp-executable", "cpp-lang", "cpp-library",
                "cunit", "cunit-test-suite",
                "distribution",
                "ear", "eclipse", "eclipse-wtp",
                "findbugs",
                "google-test", "google-test-test-suite",
                "groovy", "groovy-base",
                "idea", "ivy-publish",
                "jacoco", "java", "java-base", "java-gradle-plugin", "java-lang",
                "java-library", "java-library-distribution", "jdepend", "junit-test-suite",
                "jvm-component", "jvm-resources",
                "maven", "maven-publish",
                "native-component", "native-component-model",
                "objective-c", "objective-c-lang", "objective-cpp", "objective-cpp-lang", "osgi",
                "play", "play-application", "play-cofeescript", "play-ide", "play-javascript", "pmd",
                "reporting-base",
                "scala", "scala-base", "signing",
                "visual-studio",
                "war",
                "windows-resource-script", "windows-resources", "wrapper")
        linkedPlugins.forEach {
            assertThat(
                UserGuideLink.forPlugin(it),
                notNullValue())
        }
    }

    @Test
    fun `unlinked plugins`() {
        val unlinkedPlugins =
            listOf(
                "binary-base", "coffeescript-base", "compare-gradle-builds", "component-base",
                "component-model-base", "envjs", "gcc-compiler", "help-tasks",
                "javascript-base", "jshint", "language-base", "lifecycle-base",
                "microsoft-visual-cpp-compiler", "project-report", "project-reports",
                "publishing", "rhino", "scala-lang", "standard-tool-chains")
        unlinkedPlugins.forEach {
            assertThat(
                UserGuideLink.forPlugin(it),
                nullValue())
        }
    }
}
