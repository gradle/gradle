/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil
import org.iq80.snappy.SnappyFramedInputStream
import org.iq80.snappy.SnappyFramedOutputStream

class CacheTaskArchiveErrorIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def setup() {
        executer.beforeExecute { it.withBuildCacheEnabled() }
    }

    def remoteCacheDir = file("remote-cache-dir")

    def "describes error while packing archive"() {
        when:
        file("input.txt") << "data"

        // Just a way to induce a packing error, i.e. corrupt/partial archive
        buildFile << """
            apply plugin: "base"
            task customTask {
                inputs.file "input.txt"
                outputs.file "build/output" withPropertyName "output"
                outputs.cacheIf { true }
                doLast {
                  mkdir('build/output')
                  file('build/output/output.txt').text = file('input.txt').text
                }
            }
        """

        then:
        executer.withStackTraceChecksDisabled()
        succeeds "customTask"
        output =~ /Failed to store cache entry .+ for task ':customTask'/
        output =~ /Could not pack property 'output'/
        listCacheFiles().empty
        listCacheTempFiles().empty

        when:
        buildFile << """
            customTask {
                actions = []
                doLast {
                    mkdir("build") 
                    file("build/output").text = "text" 
                }
            }
        """

        then:
        succeeds("clean", "customTask")
        ":customTask" in executedTasks
    }

    def "archive is not pushed to remote when packing fails"() {
        when:
        file("input.txt") << "data"
        enableRemote()

        // Just a way to induce a packing error, i.e. corrupt/partial archive
        buildFile << """
            apply plugin: "base"
            task customTask {
                inputs.file "input.txt"
                outputs.file "build/output" withPropertyName "output"
                outputs.cacheIf { true }
                doLast {
                  mkdir('build/output')
                  file('build/output/output.txt').text = file('input.txt').text
                }
            }
        """

        then:
        executer.withStackTraceChecksDisabled()
        succeeds "customTask"
        listCacheFiles(remoteCacheDir).empty
        output =~ /org.gradle.api.GradleException: Could not pack property 'output'/
    }

    TestFile enableRemote() {
        settingsFile << """
            buildCache {
                remote(DirectoryBuildCache) {
                    push = true
                    directory = '${TextUtil.escapeString(remoteCacheDir.absolutePath)}'
                }
            }
        """
    }

    def "corrupt archive loaded from remote cache is not copied into local cache"() {
        when:
        file("input.txt") << "data"
        enableRemote()
        buildFile << """
            apply plugin: "base"
            task customTask {
                inputs.file "input.txt"
                outputs.file "build/output" withPropertyName "output"
                outputs.cacheIf { true }
                doLast {
                  mkdir('build')
                  file('build/output').text = file('input.txt').text
                }
            }
        """
        succeeds("customTask")

        then:
        listCacheFiles(remoteCacheDir).size() == 1

        when:
        listCacheFiles(remoteCacheDir).first().text = "corrupt"
        listCacheFiles()*.delete()

        then:
        executer.withStackTraceChecksDisabled()
        succeeds("clean", "customTask")
        output =~ /Build cache entry .+ from remote build cache is invalid/
        output =~ /Caused by: java.io.EOFException: encountered EOF while reading stream header/

        and:
        listCacheFiles().size() == 1
        listCacheFiles().first().text != "corrupt"

        when:
        settingsFile << """
            buildCache.remote.enabled = false
        """

        then:
        succeeds("clean", "customTask")
        ":customTask" in executedTasks
    }

    def "corrupt archive loaded from local cache is purged"() {
        when:
        file("input.txt") << "data"
        buildFile << """
            apply plugin: "base"
            task customTask {
                inputs.file "input.txt"
                outputs.file "build/output" withPropertyName "output"
                outputs.cacheIf { true }
                doLast {
                  mkdir('build')
                  file('build/output').text = file('input.txt').text
                }
            }
        """
        succeeds("customTask")

        then:
        listCacheFiles().size() == 1

        when:
        listCacheFiles().first().bytes = listCacheFiles().first().bytes[0..-100]

        then:
        executer.withStackTraceChecksDisabled()
        succeeds("clean", "customTask")
        output =~ /Cleaning outputs for task ':customTask' after failed load from cache/
        output =~ /Failed to load cache entry for task ':customTask', falling back to executing task/
        output =~ /Build cache entry .+ from local build cache is invalid/
        output =~ /java.io.EOFException: unexpectd EOF when reading frame/

        and:
        listCacheFiles().size() == 1
        listCacheFailedFiles().size() == 1

        and:
        succeeds("clean", "customTask")
        ":customTask" in executedTasks
    }

    def "corrupted cache provides useful error message"() {
        when:
        buildFile << """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputDirectory File outputDir = new File(temporaryDir, 'output')
                @TaskAction
                void generate() {
                    new File(outputDir, "output").text = "OK"
                }
            }

            task cacheable(type: CustomTask)
        """
        succeeds("cacheable")

        then:
        def cacheFiles = listCacheFiles()
        cacheFiles.size() == 1

        when:
        file("build").deleteDir()

        and:
        executer.withStackTraceChecksDisabled()
        corruptMetadata({ metadata -> metadata.text = "corrupt" })
        succeeds("cacheable")

        then:
        output =~ /Cached result format error, corrupted origin metadata\./
        listCacheFailedFiles().size() == 1

        when:
        file("build").deleteDir()

        then:
        succeeds("cacheable")
    }


    def corruptMetadata(Closure corrupter) {
        def cacheFiles = listCacheFiles()
        assert cacheFiles.size() == 1
        def cacheEntry = cacheFiles[0]

        def uncompressedTarCacheEntry = temporaryFolder.file("cache.tar")
        def input = new FileInputStream(cacheEntry)
        uncompressedTarCacheEntry.bytes = new SnappyFramedInputStream(input, true).bytes
        input.close()
        cacheEntry.delete()

        def extractDir = temporaryFolder.file("extract")
        uncompressedTarCacheEntry.untarTo(extractDir)
        uncompressedTarCacheEntry.delete()

        corrupter(extractDir.file("METADATA"))

        extractDir.tarTo(uncompressedTarCacheEntry)
        def output = new SnappyFramedOutputStream(new FileOutputStream(cacheEntry))
        output << uncompressedTarCacheEntry.bytes
        output.close()
    }

}
