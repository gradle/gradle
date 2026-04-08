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

import spock.lang.Issue

class UndeclaredBuildInputsDynamicGroovyBuildScriptIntegrationTest extends AbstractUndeclaredBuildInputsIntegrationTest implements GroovyPluginImplementation {
    @Override
    String getLocation() {
        return "Build file 'build.gradle'"
    }

    @Override
    void buildLogicApplication(BuildInputRead read) {
        groovyDsl(buildFile, read)
    }

    @Issue("https://github.com/gradle/gradle/issues/33317")
    def "invalidates configuration cache when a deeply nested file observed via recursive NIO #apiName is removed"() {
        def configurationCache = newConfigurationCacheFixture()

        def treeRoot = testDirectory.createDir("treeRoot")
        def marker = treeRoot.file("a/b/c/doit").createFile()

        buildFile << """
            def treeRootPath = rootDir.toPath().resolve('treeRoot')
            $walkSnippet
            tasks.register('report') {
                doLast { println('ok') }
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
        "Files.walkFileTree(2-arg)"             | 'java.nio.file.Files.walkFileTree(treeRootPath, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {})'

        "Files.walkFileTree(4-arg)"             | 'java.nio.file.Files.walkFileTree(treeRootPath, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), Integer.MAX_VALUE, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {})'

        "Files.walk(path)"                      | 'java.nio.file.Files.walk(treeRootPath).withCloseable { it.count() }'

        "Files.walk(path, maxDepth)"            | 'java.nio.file.Files.walk(treeRootPath, Integer.MAX_VALUE).withCloseable { it.count() }'

        "Files.walk(path, maxDepth, options)"   | 'java.nio.file.Files.walk(treeRootPath, Integer.MAX_VALUE, java.nio.file.FileVisitOption.FOLLOW_LINKS).withCloseable { it.count() }'

        // Deliberately never-matching predicate: Files.find still has to list every directory to decide, so we must fingerprint those listings even when the predicate yields no results.
        "Files.find(path, maxDepth, predicate)" | 'java.nio.file.Files.find(treeRootPath, Integer.MAX_VALUE, { p, attrs -> false } as java.util.function.BiPredicate).withCloseable { it.count() }'
    }

    @Issue("https://github.com/gradle/gradle/issues/33317")
    def "invalidates configuration cache when a new deeply nested file is added under a directory walked via recursive NIO #apiName"() {
        def configurationCache = newConfigurationCacheFixture()

        def treeRoot = testDirectory.createDir("treeRoot")
        treeRoot.createDir("a/b/c")

        buildFile << """
            def treeRootPath = rootDir.toPath().resolve('treeRoot')
            $walkSnippet
            tasks.register('report') {
                doLast { println('ok') }
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
        "Files.walkFileTree(2-arg)"             | 'java.nio.file.Files.walkFileTree(treeRootPath, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {})'

        "Files.walkFileTree(4-arg)"             | 'java.nio.file.Files.walkFileTree(treeRootPath, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), Integer.MAX_VALUE, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {})'

        "Files.walk(path)"                      | 'java.nio.file.Files.walk(treeRootPath).withCloseable { it.count() }'

        "Files.walk(path, maxDepth)"            | 'java.nio.file.Files.walk(treeRootPath, Integer.MAX_VALUE).withCloseable { it.count() }'

        "Files.walk(path, maxDepth, options)"   | 'java.nio.file.Files.walk(treeRootPath, Integer.MAX_VALUE, java.nio.file.FileVisitOption.FOLLOW_LINKS).withCloseable { it.count() }'

        // Deliberately never-matching predicate: Files.find still has to list every directory to decide, so we must fingerprint those listings even when the predicate yields no results.
        "Files.find(path, maxDepth, predicate)" | 'java.nio.file.Files.find(treeRootPath, Integer.MAX_VALUE, { p, attrs -> false } as java.util.function.BiPredicate).withCloseable { it.count() }'
    }
}
