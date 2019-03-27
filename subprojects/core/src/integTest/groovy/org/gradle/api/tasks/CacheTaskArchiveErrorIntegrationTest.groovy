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
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import static org.gradle.api.tasks.LocalStateFixture.defineTaskWithLocalState

class CacheTaskArchiveErrorIntegrationTest extends AbstractIntegrationSpec {

    def localCache = new TestBuildCache(file("local-cache"))
    def remoteCache = new TestBuildCache(file("remote-cache"))

    def setup() {
        executer.beforeExecute { withBuildCacheEnabled() }
        settingsFile << localCache.localCacheConfiguration()
    }

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
        output =~ /Failed to store cache entry .+/
        output =~ /Could not pack tree 'output'/
        localCache.empty
        localCache.listCacheTempFiles().empty

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
        settingsFile << remoteCache.remoteCacheConfiguration()

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
        remoteCache.empty
        output =~ /org.gradle.api.GradleException: Could not pack tree 'output'/
    }


    def "corrupt archive loaded from remote cache is not copied into local cache"() {
        when:
        file("input.txt") << "data"
        settingsFile << remoteCache.remoteCacheConfiguration()
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
        remoteCache.listCacheFiles().size() == 1

        when:
        remoteCache.listCacheFiles().first().text = "corrupt"
        localCache.listCacheFiles()*.delete()

        then:
        executer.withStackTraceChecksDisabled()
        succeeds("clean", "customTask")
        output =~ /Build cache entry .+ from remote build cache is invalid/
        output =~ /java.util.zip.ZipException: Not in GZIP format/

        and:
        localCache.listCacheFiles().size() == 1
        localCache.listCacheFiles().first().text != "corrupt"

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
        localCache.listCacheFiles().size() == 1

        when:
        localCache.listCacheFiles().first().bytes = localCache.listCacheFiles().first().bytes[0..-100]

        then:
        executer.withStackTraceChecksDisabled()
        succeeds("clean", "customTask")
        output =~ /Failed to load cache entry for task ':customTask', cleaning outputs and falling back to \(non-incremental\) execution/
        output =~ /Build cache entry .+ from local build cache is invalid/
        output =~ /java.io.EOFException: Unexpected end of ZLIB input stream/

        and:
        localCache.listCacheFiles().size() == 1
        localCache.listCacheFailedFiles().size() == 1

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
        localCache.listCacheFiles().size() == 1

        when:
        cleanBuildDir()

        and:
        executer.withStackTraceChecksDisabled()
        corruptMetadata({ metadata -> metadata.text = "corrupt" })
        succeeds("cacheable")

        then:
        output =~ /Cached result format error, corrupted origin metadata\./
        localCache.listCacheFailedFiles().size() == 1

        when:
        file("build").deleteDir()

        then:
        succeeds("cacheable")
    }

    def "corrupted cache disables incremental execution"() {
        when:
        buildFile << """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputDirectory File outputDir = new File(temporaryDir, 'output')
                @TaskAction
                void generate(IncrementalTaskInputs inputs) {
                    println "> Incremental: \${inputs.incremental}"
                    new File(outputDir, "output").text = "OK"
                }
            }

            task cacheable(type: CustomTask)
        """
        succeeds("cacheable")

        then:
        localCache.listCacheFiles().size() == 1

        when:
        cleanBuildDir()

        and:
        executer.withStackTraceChecksDisabled()
        corruptMetadata({ metadata -> metadata.text = "corrupt" })
        succeeds("cacheable")

        then:
        output =~ /Cached result format error, corrupted origin metadata\./
        output =~ /> Incremental: false/
        localCache.listCacheFailedFiles().size() == 1
    }

    @Unroll
    def "local state declared via #api API is destroyed when task fails to load from cache"() {
        def localStateFile = file("local-state.json")
        buildFile << defineTaskWithLocalState(useRuntimeApi)

        when:
        succeeds "customTask"
        then:
        executedAndNotSkipped ":customTask"
        localStateFile.assertIsFile()

        when:
        cleanBuildDir()
        executer.withStackTraceChecksDisabled()
        corruptMetadata({ metadata -> metadata.text = "corrupt" })
        succeeds "customTask", "-PassertNoLocalState"
        then:
        executedAndNotSkipped ":customTask"

        where:
        useRuntimeApi << [true, false]
        api = useRuntimeApi ? "runtime" : "annotation"
    }

    private TestFile cleanBuildDir() {
        file("build").deleteDir()
    }

    def corruptMetadata(Closure corrupter) {
        def cacheFiles = localCache.listCacheFiles()
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
