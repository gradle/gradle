/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.archive.TarTestFixture

class CachedTaskIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def "displays info about local build cache configuration"() {
        buildFile << defineCacheableTask()
        withBuildCache()
        succeeds "cacheable", "--info"

        expect:
        outputContains "Using local directory build cache for the root build (location = ${cacheDir}, removeUnusedEntriesAfter = 7 days)."
    }

    def "cache entry contains expected contents"() {
        buildFile << defineCacheableTask()
        when:
        withBuildCache().run("cacheable")
        then:
        def cacheFiles = listCacheFiles()
        cacheFiles.size() == 1
        def cacheEntry = new TarTestFixture(cacheFiles[0])
        cacheEntry.assertContainsFile("tree-outputDir/output")
        def metadata = cacheEntry.content("METADATA")
        metadata.contains("type=")
        metadata.contains("identity=")
        metadata.contains("gradleVersion=")
        metadata.contains("creationTime=")
        metadata.contains("executionTime=")
        metadata.contains("operatingSystem=")
        metadata.contains("hostName=")
        metadata.contains("userName=")
    }

    def "storing in the cache can be disabled"() {
        buildFile << defineCacheableTask()
        buildFile << """
            apply plugin: 'base'

            def storeInCache = project.hasProperty('storeInCache')
            cacheable.doLast {
                if (!storeInCache) {
                    outputs.doNotStoreInCache()
                }
            }
        """

        when:
        withBuildCache().run("cacheable")

        then:
        listCacheFiles().empty

        when:
        withBuildCache().run("clean", "cacheable", "-PstoreInCache")

        then:
        listCacheFiles().size() == 1

        when:
        withBuildCache().run("clean", "cacheable")

        then:
        skipped ":cacheable"
    }

    def "task is cacheable after previous failure"() {
        buildFile << """
            task foo {
                def outFile = project.file("out.txt")
                outputs.file(outFile)
                outputs.cacheIf { true }
                doLast {
                    outFile << "xxx"
                    if (System.getProperty("fail")) {
                        throw new RuntimeException("Boo!")
                    }
                }
            }
        """

        expect:
        executer.withStackTraceChecksDisabled()
        withBuildCache().fails "foo", "-Dfail=yes"

        when:
        withBuildCache().run "foo"
        then:
        result.assertTasksExecuted(":foo")

        when:
        withBuildCache().run "foo"
        then:
        skipped ":foo"
    }

    def "task is loaded from cache when returning to already cached state after failure"() {
        buildFile << """
            task foo {
                inputs.property("change", project.hasProperty("change"))
                def outTxt = file('out.txt')
                outputs.file(outTxt)
                outputs.cacheIf { true }
                def fail = providers.gradleProperty('fail')
                doLast {
                    outTxt << "xxx"
                    if (fail.isPresent()) {
                        throw new RuntimeException("Boo!")
                    }
                }
            }
        """

        // Cache original
        withBuildCache().run "foo"

        // Fail with a change
        executer.withStackTraceChecksDisabled()
        withBuildCache().fails "foo", "-Pfail", "-Pchange"

        // Re-running without change should load from cache
        when:
        withBuildCache().run "foo"
        then:
        skipped ":foo"
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FLAKY)
    def "displays info about loading and storing in cache"() {
        buildFile << defineCacheableTask()
        when:
        withBuildCache().run "cacheable", "--info"
        then:
        outputContains "Stored cache entry for task ':cacheable' with cache key"

        file("build").deleteDir()

        when:
        withBuildCache().run "cacheable", "--info"
        then:
        outputContains "Loaded cache entry for task ':cacheable' with cache key"
    }

    def defineCacheableTask() {
        """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputDirectory File outputDir = new File(project.buildDir, 'output')
                @TaskAction
                void generate() {
                    new File(outputDir, "output").text = "OK"
                }
            }

            task cacheable(type: CustomTask)
        """
    }

}
