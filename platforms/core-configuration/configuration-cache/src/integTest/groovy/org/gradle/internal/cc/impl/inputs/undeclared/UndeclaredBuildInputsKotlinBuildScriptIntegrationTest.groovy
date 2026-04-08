/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl.inputs.undeclared

import org.gradle.test.fixtures.Flaky
import spock.lang.Issue

@Flaky(because = "https://github.com/gradle/gradle-private/issues/4440")
class UndeclaredBuildInputsKotlinBuildScriptIntegrationTest extends AbstractUndeclaredBuildInputsIntegrationTest implements KotlinPluginImplementation {
    @Override
    String getLocation() {
        return "Build file 'build.gradle.kts'"
    }

    @Override
    void buildLogicApplication(BuildInputRead read) {
        kotlinDsl(buildKotlinFile, read)
    }

    @Issue("https://github.com/gradle/gradle/issues/33317")
    def "invalidates configuration cache when a file observed via Kotlin File.#walkMethod is removed"() {
        def configurationCache = newConfigurationCacheFixture()

        def treeRoot = testDirectory.createDir("treeRoot")
        def marker = treeRoot.file("a/b/c/doit").createFile()

        buildKotlinFile << """
            rootDir.resolve("treeRoot").${walkMethod}.forEach {
                if (it.name == "doit") {
                    tasks.register("doit") {
                        doLast { println("Doing the thing!") }
                    }
                }
            }
        """

        when: "the marker file exists during configuration"
        configurationCacheRunLenient("doit")

        then: "the task runs and the configuration cache is stored"
        configurationCache.assertStateStored()
        outputContains("Doing the thing!")

        when: "the build is re-run with no filesystem changes"
        configurationCacheRunLenient("doit")

        then: "the cache entry is reused"
        configurationCache.assertStateLoaded()
        outputContains("Doing the thing!")

        when: "the marker file is removed"
        marker.delete()
        configurationCacheFails("doit")

        then: "the cache entry is invalidated, configuration re-runs and the task is no longer registered"
        configurationCache.assertLoadPhaseSkipped()
        failure.assertHasDescription("Task 'doit' not found in root project")

        where:
        walkMethod << [
            "walk()",
            "walk(kotlin.io.FileWalkDirection.TOP_DOWN)",
            "walk(kotlin.io.FileWalkDirection.BOTTOM_UP)",
            "walkTopDown()",
            "walkBottomUp()"
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/33317")
    def "invalidates configuration cache when a new file is added under a directory walked via Kotlin File.#walkMethod"() {
        def configurationCache = newConfigurationCacheFixture()

        def treeRoot = testDirectory.createDir("treeRoot")
        treeRoot.createDir("a/b/c")

        buildKotlinFile << """
            val names = mutableListOf<String>()
            rootDir.resolve("treeRoot").${walkMethod}.forEach { names += it.name }
            tasks.register("report") {
                val captured = names.joinToString(",")
                doLast { println("walked=\$captured") }
            }
        """

        when: "the initial tree is walked during configuration"
        configurationCacheRunLenient("report")

        then: "the configuration cache is stored"
        configurationCache.assertStateStored()
        outputContains("walked=")

        when: "a new file is added deep in the walked tree"
        treeRoot.file("a/b/c/newFile").createFile()
        configurationCacheRunLenient("report")

        then: "the cache entry is invalidated"
        configurationCache.assertStateStored()

        where:
        walkMethod << [
            "walk()",
            "walk(kotlin.io.FileWalkDirection.TOP_DOWN)",
            "walk(kotlin.io.FileWalkDirection.BOTTOM_UP)",
            "walkTopDown()",
            "walkBottomUp()"
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/33317")
    def "invalidates configuration cache when a deeply nested file observed via recursive NIO #apiName is removed"() {
        def configurationCache = newConfigurationCacheFixture()

        def treeRoot = testDirectory.createDir("treeRoot")
        def marker = treeRoot.file("a/b/c/doit").createFile()

        buildKotlinFile << """
            val treeRootPath: java.nio.file.Path = rootDir.resolve("treeRoot").toPath()
            $walkSnippet
            tasks.register("report") {
                doLast { println("ok") }
            }
        """

        when: "the tree is walked during configuration"
        configurationCacheRunLenient("report")

        then: "the configuration cache is stored"
        configurationCache.assertStateStored()

        when: "the build is re-run with no filesystem changes"
        configurationCacheRunLenient("report")

        then: "the cache entry is reused"
        configurationCache.assertStateLoaded()

        when: "a deeply nested file is removed"
        marker.delete()
        configurationCacheRunLenient("report")

        then: "the cache entry is invalidated"
        configurationCache.assertStateStored()

        where:
        apiName                                 | walkSnippet
        "Files.walkFileTree(2-arg)"             | 'java.nio.file.Files.walkFileTree(treeRootPath, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {})'

        "Files.walkFileTree(4-arg)"             | 'java.nio.file.Files.walkFileTree(treeRootPath, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption::class.java), Int.MAX_VALUE, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {})'

        "Files.walk(path)"                      | 'java.nio.file.Files.walk(treeRootPath).use { it.count() }'

        "Files.walk(path, maxDepth)"            | 'java.nio.file.Files.walk(treeRootPath, Int.MAX_VALUE).use { it.count() }'

        "Files.walk(path, maxDepth, options)"   | 'java.nio.file.Files.walk(treeRootPath, Int.MAX_VALUE, java.nio.file.FileVisitOption.FOLLOW_LINKS).use { it.count() }'

        // Deliberately never-matching predicate: Files.find still has to list every directory to decide, so we must fingerprint those listings even when the predicate yields no results.
        "Files.find(path, maxDepth, predicate)" | 'java.nio.file.Files.find(treeRootPath, Int.MAX_VALUE, java.util.function.BiPredicate { _, _ -> false }).use { it.count() }'
    }

    @Issue("https://github.com/gradle/gradle/issues/33317")
    def "invalidates configuration cache when a new deeply nested file is added under a directory walked via recursive NIO #apiName"() {
        def configurationCache = newConfigurationCacheFixture()

        def treeRoot = testDirectory.createDir("treeRoot")
        treeRoot.createDir("a/b/c")

        buildKotlinFile << """
            val treeRootPath: java.nio.file.Path = rootDir.resolve("treeRoot").toPath()
            $walkSnippet
            tasks.register("report") {
                doLast { println("ok") }
            }
        """

        when: "the initial tree is walked during configuration"
        configurationCacheRunLenient("report")

        then: "the configuration cache is stored"
        configurationCache.assertStateStored()

        when: "a new file is added deep in the walked tree"
        treeRoot.file("a/b/c/newFile").createFile()
        configurationCacheRunLenient("report")

        then: "the cache entry is invalidated"
        configurationCache.assertStateStored()

        where:
        apiName                                 | walkSnippet
        "Files.walkFileTree(2-arg)"             | 'java.nio.file.Files.walkFileTree(treeRootPath, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {})'

        "Files.walkFileTree(4-arg)"             | 'java.nio.file.Files.walkFileTree(treeRootPath, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption::class.java), Int.MAX_VALUE, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {})'

        "Files.walk(path)"                      | 'java.nio.file.Files.walk(treeRootPath).use { it.count() }'

        "Files.walk(path, maxDepth)"            | 'java.nio.file.Files.walk(treeRootPath, Int.MAX_VALUE).use { it.count() }'

        "Files.walk(path, maxDepth, options)"   | 'java.nio.file.Files.walk(treeRootPath, Int.MAX_VALUE, java.nio.file.FileVisitOption.FOLLOW_LINKS).use { it.count() }'

        // Deliberately never-matching predicate: Files.find still has to list every directory to decide, so we must fingerprint those listings even when the predicate yields no results.
        "Files.find(path, maxDepth, predicate)" | 'java.nio.file.Files.find(treeRootPath, Int.MAX_VALUE, java.util.function.BiPredicate { _, _ -> false }).use { it.count() }'
    }
}
