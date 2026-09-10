/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.cc.impl.inputs.filewalk

import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl
import org.junit.Assume
import spock.lang.Issue

abstract class AbstractFileWalkIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    /**
     * Applies a build logic snippet to the appropriate location.
     * The closure receives a {@link GradleDsl} and must return the snippet as a String.
     */
    abstract void buildLogicApplication(Closure<String> snippetProvider)

    @Issue("https://github.com/gradle/gradle/issues/33317")
    def "invalidates cache when file walked via #apiName is removed"() {
        def configurationCache = newConfigurationCacheFixture()

        def treeRoot = testDirectory.createDir("treeRoot")
        def marker = treeRoot.file("a/b/c/doit").createFile()

        buildLogicApplication { GradleDsl dsl -> walkSnippet(dsl) }

        when: "the marker file exists during configuration"
        configurationCacheRunLenient("doit")

        then: "the task is registered and the configuration cache is stored"
        configurationCache.assertStateStored()
        outputContains("Doing the thing!")

        when: "the marker file is removed"
        marker.delete()
        configurationCacheFails("doit")

        then: "the cache entry is invalidated, configuration re-runs and the task is no longer registered"
        configurationCache.assertLoadPhaseSkipped()
        failure.assertHasDescription("Task 'doit' not found in root project")

        where:
        apiName                                                         | walkSnippet
        "java.nio.file.Files.walkFileTree(Path, FileVisitor)"           | { GradleDsl d -> nioVisitorSnippet(d, "java.nio.file.Files.walkFileTree(treeRootPath, visitor)") }
        "java.nio.file.Files.walkFileTree(Path, Set, int, FileVisitor)" | { GradleDsl d -> nioVisitorSnippet(d, "java.nio.file.Files.walkFileTree(treeRootPath, ${enumSetNoneOf(d)}, ${maxDepth(d)}, visitor)") }
        "java.nio.file.Files.walk(Path)"                                | { GradleDsl d -> nioStreamSnippet(d, "java.nio.file.Files.walk(treeRootPath)") }
        "java.nio.file.Files.walk(Path, int)"                           | { GradleDsl d -> nioStreamSnippet(d, "java.nio.file.Files.walk(treeRootPath, ${maxDepth(d)})") }
        "java.nio.file.Files.walk(Path, int, FileVisitOption)"          | { GradleDsl d -> nioStreamSnippet(d, "java.nio.file.Files.walk(treeRootPath, ${maxDepth(d)}, java.nio.file.FileVisitOption.FOLLOW_LINKS)") }
        "java.nio.file.Files.find(Path, int, BiPredicate)"              | { GradleDsl d -> nioFindSnippet(d) }
        "kotlin.io.File.walk()"                                         | { GradleDsl d -> kotlinWalkSnippet(d, "walk()") }
        "kotlin.io.File.walk(FileWalkDirection.TOP_DOWN)"               | { GradleDsl d -> kotlinWalkSnippet(d, "walk(kotlin.io.FileWalkDirection.TOP_DOWN)") }
        "kotlin.io.File.walk(FileWalkDirection.BOTTOM_UP)"              | { GradleDsl d -> kotlinWalkSnippet(d, "walk(kotlin.io.FileWalkDirection.BOTTOM_UP)") }
        "kotlin.io.File.walkTopDown()"                                  | { GradleDsl d -> kotlinWalkSnippet(d, "walkTopDown()") }
        "kotlin.io.File.walkBottomUp()"                                 | { GradleDsl d -> kotlinWalkSnippet(d, "walkBottomUp()") }
    }

    @Issue("https://github.com/gradle/gradle/issues/33317")
    def "invalidates cache when file is added to directory walked via #apiName"() {
        def configurationCache = newConfigurationCacheFixture()

        def treeRoot = testDirectory.createDir("treeRoot")
        treeRoot.createDir("a/b/c")

        buildLogicApplication { GradleDsl dsl -> walkSnippet(dsl) }

        when: "the initial tree is walked during configuration (no marker file)"
        configurationCacheRunLenient("help")

        then: "the configuration cache is stored"
        configurationCache.assertStateStored()

        when: "a new marker file is added deep in the walked tree"
        treeRoot.file("a/b/c/doit").createFile()
        configurationCacheRunLenient("doit")

        then: "the cache entry is invalidated and the new task is registered"
        configurationCache.assertStateStored()
        outputContains("Doing the thing!")

        where:
        apiName                                                         | walkSnippet
        "java.nio.file.Files.walkFileTree(Path, FileVisitor)"           | { GradleDsl d -> nioVisitorSnippet(d, "java.nio.file.Files.walkFileTree(treeRootPath, visitor)") }
        "java.nio.file.Files.walkFileTree(Path, Set, int, FileVisitor)" | { GradleDsl d -> nioVisitorSnippet(d, "java.nio.file.Files.walkFileTree(treeRootPath, ${enumSetNoneOf(d)}, ${maxDepth(d)}, visitor)") }
        "java.nio.file.Files.walk(Path)"                                | { GradleDsl d -> nioStreamSnippet(d, "java.nio.file.Files.walk(treeRootPath)") }
        "java.nio.file.Files.walk(Path, int)"                           | { GradleDsl d -> nioStreamSnippet(d, "java.nio.file.Files.walk(treeRootPath, ${maxDepth(d)})") }
        "java.nio.file.Files.walk(Path, int, FileVisitOption)"          | { GradleDsl d -> nioStreamSnippet(d, "java.nio.file.Files.walk(treeRootPath, ${maxDepth(d)}, java.nio.file.FileVisitOption.FOLLOW_LINKS)") }
        "java.nio.file.Files.find(Path, int, BiPredicate)"              | { GradleDsl d -> nioFindSnippet(d) }
        "kotlin.io.File.walk()"                                         | { GradleDsl d -> kotlinWalkSnippet(d, "walk()") }
        "kotlin.io.File.walk(FileWalkDirection.TOP_DOWN)"               | { GradleDsl d -> kotlinWalkSnippet(d, "walk(kotlin.io.FileWalkDirection.TOP_DOWN)") }
        "kotlin.io.File.walk(FileWalkDirection.BOTTOM_UP)"              | { GradleDsl d -> kotlinWalkSnippet(d, "walk(kotlin.io.FileWalkDirection.BOTTOM_UP)") }
        "kotlin.io.File.walkTopDown()"                                  | { GradleDsl d -> kotlinWalkSnippet(d, "walkTopDown()") }
        "kotlin.io.File.walkBottomUp()"                                 | { GradleDsl d -> kotlinWalkSnippet(d, "walkBottomUp()") }
    }

    /**
     * Generates a snippet that uses {@code Files.walkFileTree} with a visitor that registers a task
     * when it finds a file named "doit".
     */
    protected static String nioVisitorSnippet(GradleDsl dsl, String walkCall) {
        switch (dsl) {
            case GradleDsl.KOTLIN:
                return """\
                    val treeRootPath: java.nio.file.Path = rootDir.resolve("treeRoot").toPath()
                    val visitor = object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                        override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                            if (file.fileName.toString() == "doit") {
                                tasks.register("doit") { doLast { println("Doing the thing!") } }
                            }
                            return java.nio.file.FileVisitResult.CONTINUE
                        }
                    }
                    ${walkCall}""".stripIndent()
            default:
                return """\
                    def treeRootPath = rootDir.toPath().resolve('treeRoot')
                    def visitor = new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                        java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                            if (file.fileName.toString() == 'doit') {
                                tasks.register('doit') { doLast { println('Doing the thing!') } }
                            }
                            return java.nio.file.FileVisitResult.CONTINUE
                        }
                    }
                    ${walkCall}""".stripIndent()
        }
    }

    /**
     * Generates a snippet that uses {@code Files.walk} (stream-based) and registers a task
     * when it finds a file named "doit".
     */
    protected static String nioStreamSnippet(GradleDsl dsl, String streamExpr) {
        switch (dsl) {
            case GradleDsl.KOTLIN:
                return """\
                    val treeRootPath: java.nio.file.Path = rootDir.resolve("treeRoot").toPath()
                    ${streamExpr}.use { stream ->
                        stream.forEach { path ->
                            if (path.fileName.toString() == "doit") {
                                tasks.register("doit") { doLast { println("Doing the thing!") } }
                            }
                        }
                    }""".stripIndent()
            default:
                return """\
                    def treeRootPath = rootDir.toPath().resolve('treeRoot')
                    ${streamExpr}.withCloseable { stream ->
                        stream.forEach { path ->
                            if (path.fileName.toString() == 'doit') {
                                tasks.register('doit') { doLast { println('Doing the thing!') } }
                            }
                        }
                    }""".stripIndent()
        }
    }

    /**
     * Generates a snippet that uses {@code Files.find} with a predicate matching "doit"
     * and registers a task if found.
     */
    protected static String nioFindSnippet(GradleDsl dsl) {
        switch (dsl) {
            case GradleDsl.KOTLIN:
                return """\
                    val treeRootPath: java.nio.file.Path = rootDir.resolve("treeRoot").toPath()
                    java.nio.file.Files.find(treeRootPath, Int.MAX_VALUE, java.util.function.BiPredicate { path, _ -> path.fileName.toString() == "doit" }).use { stream ->
                        if (stream.findFirst().isPresent) {
                            tasks.register("doit") { doLast { println("Doing the thing!") } }
                        }
                    }""".stripIndent()
            default:
                return """\
                    def treeRootPath = rootDir.toPath().resolve('treeRoot')
                    java.nio.file.Files.find(treeRootPath, Integer.MAX_VALUE, { path, attrs -> path.fileName.toString() == 'doit' } as java.util.function.BiPredicate).withCloseable { stream ->
                        if (stream.findFirst().isPresent()) {
                            tasks.register('doit') { doLast { println('Doing the thing!') } }
                        }
                    }""".stripIndent()
        }
    }

    protected static String kotlinWalkSnippet(GradleDsl dsl, String walkCall) {
        Assume.assumeTrue("Kotlin File.walk() is only available in Kotlin DSL", dsl == GradleDsl.KOTLIN)
        """\
            rootDir.resolve("treeRoot").${walkCall}.forEach {
                if (it.name == "doit") {
                    tasks.register("doit") { doLast { println("Doing the thing!") } }
                }
            }""".stripIndent()
    }

    protected static String enumSetNoneOf(GradleDsl dsl) {
        switch (dsl) {
            case GradleDsl.KOTLIN:
                return 'java.util.EnumSet.noneOf(java.nio.file.FileVisitOption::class.java)'
            default:
                return 'java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class)'
        }
    }

    protected static String maxDepth(GradleDsl dsl) {
        switch (dsl) {
            case GradleDsl.KOTLIN:
                return 'Int.MAX_VALUE'
            default:
                return 'Integer.MAX_VALUE'
        }
    }
}
