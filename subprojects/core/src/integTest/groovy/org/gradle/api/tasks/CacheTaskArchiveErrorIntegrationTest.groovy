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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

import java.util.function.Consumer
import java.util.function.Predicate

import static org.gradle.util.internal.TextUtil.escapeString

class CacheTaskArchiveErrorIntegrationTest extends AbstractIntegrationSpec {

    private static final String CACHE_KEY_PATTERN = "[0-9a-f]+"

    def localCache = new TestBuildCache(file("local-cache"))
    def remoteCache = new TestBuildCache(file("remote-cache"))

    def setup() {
        executer.beforeExecute { withBuildCacheEnabled() }
        settingsFile << localCache.localCacheConfiguration()
    }

    def "fails build when packing archive fails"() {
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
        fails "customTask"

        failureHasCause(~/Failed to store cache entry $CACHE_KEY_PATTERN for task ':customTask': Could not pack tree 'output': Expected '${escapeString(file('build/output'))}' to be a file/)
        errorOutput =~ /Could not pack tree 'output'/
        localCache.empty
        localCache.listCacheTempFiles().empty
    }

    def "archive is not pushed to remote when packing fails"() {
        executer.withStacktraceEnabled()

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
        fails "customTask"

        remoteCache.empty
        failureHasCause(~/Failed to store cache entry $CACHE_KEY_PATTERN for task ':customTask': Could not pack tree 'output': Expected '${escapeString(file('build/output'))}' to be a file/)
        errorOutput =~ /${RuntimeException.name}: Could not pack tree 'output'/
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
        fails("clean", "customTask")
        failureHasCause(~/Failed to load cache entry $CACHE_KEY_PATTERN for task ':customTask': Could not load from remote cache: Not in GZIP format/)

        and:
        localCache.listCacheFiles().empty
    }

    def "corrupt archive loaded from local cache is purged"() {
        executer.withStacktraceEnabled()

        when:
        file("input.txt") << "data"
        buildFile << """
            apply plugin: "base"
            task customTask {
                def inputTxt = file("input.txt")
                def outputTxt = file("build/output")
                inputs.file inputTxt
                outputs.file outputTxt withPropertyName "output"
                outputs.cacheIf { true }
                doLast {
                  outputTxt.parentFile.mkdirs()
                  outputTxt.text = inputTxt.text
                }
            }
        """
        succeeds("customTask")

        then:
        localCache.listCacheFiles().size() == 1

        when:
        localCache.listCacheFiles().first().bytes = localCache.listCacheFiles().first().bytes[0..-100]

        then:
        fails("clean", "customTask")
        failureHasCause(~/Failed to load cache entry $CACHE_KEY_PATTERN for task ':customTask': Could not load from local cache: Unexpected end of ZLIB input stream/)
        errorOutput.contains("Caused by: java.io.EOFException: Unexpected end of ZLIB input stream")
        localCache.listCacheFailedFiles().size() == 1

        and:
        localCache.listCacheFiles().empty

        when:
        file("build").deleteDir()

        then:
        succeeds("customTask")
    }

    def "corrupted cache artifact metadata provides useful error message"() {
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
        executer.withStacktraceEnabled()
        cleanBuildDir()

        and:
        corruptMetadata({ metadata -> metadata.text = "corrupt" })
        fails("cacheable")

        then:
        errorOutput.contains("Caused by: java.lang.IllegalStateException: Cached result format error, corrupted origin metadata")
        localCache.listCacheFailedFiles().size() == 1
    }

    @Requires(TestPrecondition.SYMLINKS)
    @Issue("https://github.com/gradle/gradle/issues/9906")
    def "don't cache if task produces broken symlink"() {
        def link = file('root/link')
        buildFile << """
            import java.nio.file.*
            class ProducesLink extends DefaultTask {
                @OutputDirectory File outputDirectory

                @TaskAction execute() {
                    Files.createSymbolicLink(Paths.get('${link}'), Paths.get('target'));
                }
            }

            task producesLink(type: ProducesLink) {
                outputDirectory = file 'root'
                outputs.cacheIf { true }
            }
        """

        when:
        fails "producesLink"
        then:
        failureHasCause(~/Failed to store cache entry $CACHE_KEY_PATTERN for task ':producesLink': Could not pack tree 'outputDirectory': Couldn't read content of file '${link}'/)
        errorOutput.contains("Couldn't read content of file '${link}'")
    }

    private TestFile cleanBuildDir() {
        file("build").deleteDir()
    }

    def corruptMetadata(Consumer<File> corrupter, Predicate<File> matcher = { true }) {
        localCache.listCacheFiles().each { cacheEntry ->
            println "> Considering corrupting $cacheEntry.name..."

            // Must rename to "*.tgz" for unpacking to work
            def tgzCacheEntry = temporaryFolder.file("cache.tgz")
            cacheEntry.copyTo(tgzCacheEntry)
            def extractDir = temporaryFolder.file("extract")
            tgzCacheEntry.untarTo(extractDir)
            tgzCacheEntry.delete()

            def metadataFile = extractDir.file("METADATA")
            if (matcher.test(metadataFile)) {
                println "> Corrupting $cacheEntry..."
                corrupter.accept(metadataFile)
                cacheEntry.delete()
                extractDir.tgzTo(cacheEntry)
            }
            extractDir.deleteDir()
        }
    }
}
