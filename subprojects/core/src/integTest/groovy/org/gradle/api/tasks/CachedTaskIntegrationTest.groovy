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
import org.gradle.test.fixtures.archive.TarTestFixture

class CachedTaskIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    def "produces incubation warning"() {
        buildFile << defineCacheableTask()
        withBuildCache().succeeds "cacheable"
        expect:
        result.assertOutputContains("Build cache is an incubating feature.")
    }

    def "displays info about local build cache configuration"() {
        buildFile << defineCacheableTask()
        withBuildCache().succeeds "cacheable", "--info"
        expect:
        result.assertOutputContains "Using local directory build cache for the root build (location = ${cacheDir})."
    }

    def "cache entry contains expected contents"() {
        buildFile << defineCacheableTask()
        when:
        withBuildCache().succeeds("cacheable")
        then:
        def cacheFiles = listCacheFiles()
        cacheFiles.size() == 1
        def cacheEntry = new TarTestFixture(cacheFiles[0])
        cacheEntry.assertContainsFile("property-outputDir/output")
        def metadata = cacheEntry.content("METADATA")
        metadata.contains("type=")
        metadata.contains("path=")
        metadata.contains("gradleVersion=")
        metadata.contains("creationTime=")
        metadata.contains("executionTime=")
        metadata.contains("rootPath=")
        metadata.contains("operatingSystem=")
        metadata.contains("hostName=")
        metadata.contains("userName=")
    }

    def "corrupted cache provides useful error message"() {
        buildFile << defineCacheableTask()
        when:
        withBuildCache().succeeds("cacheable")
        then:
        def cacheFiles = listCacheFiles()
        cacheFiles.size() == 1

        when:
        file("build").deleteDir()
        and:
        corruptMetadata({ metadata -> metadata.text = "corrupt" })
        withBuildCache().fails("cacheable")
        then:
        failure.assertHasCause("Cached result format error, corrupted origin metadata.")

        when:
        file("build").deleteDir()
        and:
        corruptMetadata({ metadata -> metadata.delete() })
        withBuildCache().fails("cacheable")
        then:
        failure.assertHasCause("Cached result format error, no origin metadata was found.")
    }

    def "task is cacheable after previous failure"() {
        buildFile << """
            task foo {
                outputs.file("out.txt")
                outputs.cacheIf { true }
                doLast {
                    project.file("out.txt") << "xxx"
                    if (project.hasProperty("fail")) {
                        throw new RuntimeException("Boo!")
                    }
                    assert state.taskOutputCaching.enabled
                }
            }
        """

        executer.withStackTraceChecksDisabled()

        expect:
        fails "foo", "-Pfail"

        when:
        withBuildCache().succeeds "foo"
        then:
        executedTasks == [":foo"]

        when:
        withBuildCache().succeeds "foo"
        then:
        skippedTasks as List == [":foo"]
    }

    def defineCacheableTask() {
        """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputDirectory File outputDir = temporaryDir
                @TaskAction
                void generate() {
                    new File(outputDir, "output").text = "OK"
                }
            }

            task cacheable(type: CustomTask)
        """
    }

    def corruptMetadata(Closure corrupter) {
        def cacheFiles = listCacheFiles()
        assert cacheFiles.size() == 1
        def cacheEntry = cacheFiles[0]
        def tgzCacheEntry = temporaryFolder.file("cache.tgz")
        cacheEntry.copyTo(tgzCacheEntry)
        cacheEntry.delete()
        def extractDir = temporaryFolder.file("extract")
        tgzCacheEntry.untarTo(extractDir)
        corrupter(extractDir.file("METADATA"))
        extractDir.tgzTo(cacheEntry)
    }
}
