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

package org.gradle.caching.local.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Ignore
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class DirectoryBuildCacheCleanupIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    private final static int MAX_CACHE_AGE = 7
    private TestFile gcFile

    // days
    def setup() {
        settingsFile << """
            buildCache {
                local {
                    removeUnusedEntriesAfterDays = ${MAX_CACHE_AGE}
                }
            }
        """
        def bytes = new byte[1024 * 1024]
        new Random().nextBytes(bytes)
        file("output.txt").bytes = bytes

        gcFile = cacheDir.file("gc.properties")

        buildFile << """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputFile File outputFile = new File(temporaryDir, "output.txt")
                @Input String run = project.findProperty("run") ?: ""
                @TaskAction 
                void generate() {
                    logger.warn("Run " + run)
                    project.copy {
                        from("output.txt")
                        into temporaryDir
                    }
                }
            }
            
            task cacheable(type: CustomTask) {
                description = "Generates a 1MB file"
            }
            
            task assertCacheNotCleanedUpYet {
                dependsOn cacheable
                doFirst {
                    assert file("${gcFile.toURI()}").lastModified() == Long.parseLong(project.property("lastCleanupCheck"))
                }
            }
        """
    }

    def "cleans up entries"() {
        // Make sure cache directory is initialized
        run()

        when:
        def trashFile = createOldTrashFile()
        markCacheForCleanup()
        run()
        then:
        trashFile.assertDoesNotExist()
    }

    @Unroll
    def "produces reasonable message when cache retention is too short (#days days)"() {
        settingsFile << """
            buildCache {
                local {
                    removeUnusedEntriesAfterDays = ${days}
                }
            }
        """
        expect:
        fails("help")
        result.error.contains("Directory build cache needs to retain entries for at least a day.")

        where:
        days << [-1, 0]
    }

    def "build cache cleanup is triggered after max number of days expires"() {
        def messageRegex = /Build cache \(.+\) cleaned up in .+ secs\./
        def checkInterval = 7 // days

        when:
        run()
        then:
        listCacheFiles().size() == 1
        def originalCheckTime = gcFile().lastModified()

        // One day isn't enough to trigger
        when:
        // Set the time back 1 day
        gcFile().setLastModified(originalCheckTime - TimeUnit.DAYS.toMillis(1))
        def lastCleanupCheck = gcFile().lastModified()
        withBuildCache().succeeds("cacheable", "-i")
        then:
        result.output.readLines().every {
            !it.matches(messageRegex)
        }
        gcFile().lastModified() == lastCleanupCheck

        // checkInterval-1 days is not enough to trigger
        when:
        gcFile().setLastModified(originalCheckTime - TimeUnit.DAYS.toMillis(checkInterval-1))
        lastCleanupCheck = gcFile().lastModified()
        withBuildCache().succeeds("cacheable", "-i")
        then:
        result.output.readLines().every {
            !it.matches(messageRegex)
        }
        gcFile().lastModified() == lastCleanupCheck

        // checkInterval days is enough to trigger
        when:
        gcFile().setLastModified(originalCheckTime - TimeUnit.DAYS.toMillis(checkInterval))
        withBuildCache().succeeds("cacheable", "-i")
        then:
        result.output.readLines().any {
            it.matches(messageRegex)
        }
        gcFile().lastModified() > lastCleanupCheck

        // More than checkInterval days is enough to trigger
        when:
        gcFile().setLastModified(originalCheckTime - TimeUnit.DAYS.toMillis(checkInterval*10))
        withBuildCache().succeeds("cacheable", "-i")
        then:
        result.output.readLines().any {
            it.matches(messageRegex)
        }
        gcFile().lastModified() > lastCleanupCheck
    }

    @Ignore("FIXME lptr")
    def "buildSrc does not try to clean build cache"() {
        // Copy cache configuration
        file("buildSrc/settings.gradle").text = settingsFile.text

        // Make sure cache directory is initialized
        run()
        // Witness file to signal when cache is cleaned upcvxb
        def trashFile = createOldTrashFile()

        when:
        run()
        then:
        trashFile.assertExists()

        when:
        markCacheForCleanup()
        run()
        then:
        trashFile.assertDoesNotExist()
    }

    @Ignore("FIXME lptr")
    def "composite builds do not try to clean build cache"() {
        file("included/build.gradle") << """
            apply plugin: 'java'
            group = "com.example"
            version = "2.0"
        """
        // Copy cache configuration
        file("included/settings.gradle").text = ""

        settingsFile << """
            includeBuild file("included/")
        """
        buildFile << """
            configurations {
                test
            }
            dependencies {
                test "com.example:included:1.0"
            }
            assertCacheNotCleanedUpYet {
                dependsOn cacheable, configurations.test
                doFirst {
                    println configurations.test.files
                }
            }
        """

        run()
        def lastCleanupCheck = markCacheForCleanup()

        expect:
        withBuildCache().run("assertCacheNotCleanedUpYet", "-PlastCleanupCheck=$lastCleanupCheck")
    }

    @Ignore("FIXME lptr")
    def "composite build with buildSrc do not try to clean build cache mid build"() {
        file("included/build.gradle") << """
            apply plugin: 'java'
            group = "com.example"
            version = "2.0"
        """
        // Copy cache configuration
        file("included/buildSrc/settings.gradle").text = settingsFile.text
        file("included/settings.gradle").text = settingsFile.text

        settingsFile << """
            includeBuild file("included/")
        """
        buildFile << """
            configurations {
                test
            }
            dependencies {
                test "com.example:included:1.0"
            }
            
            assertCacheNotCleanedUpYet {
                dependsOn configurations.test
                doFirst {
                    println configurations.test.files
                }
            }
        """
        when:
        run()
        then:
        def lastCleanupCheck = gcFile().makeOlder().lastModified()

        when:
        markCacheForCleanup()
        // Composite didn't clean up cache during build
        withBuildCache().succeeds("assertCacheNotCleanedUpYet", "-PlastCleanupCheck=$lastCleanupCheck")
        then:
        // build cache has been cleaned up now
        gcFile().lastModified() >= lastCleanupCheck
    }

    @Ignore("FIXME lptr")
    def "GradleBuild tasks do not try to clean build cache"() {
        // Copy cache configuration
        file("included/build.gradle") << """
            apply plugin: 'java'
            group = "com.example"
            version = "2.0"
        """
        file("included/settings.gradle").text = settingsFile.text
        buildFile << """
            task gradleBuild(type: GradleBuild) {
                dir = file("included/")
                tasks = [ "build" ]
            }
            assertBuildCacheOverTarget.dependsOn cacheable, gradleBuild
        """
        when:
        run()
        then:
        // build cache hasn't been cleaned yet
        def lastCleanupCheck = markCacheForCleanup()

        when:
        // During the build, the build cache should be over the target still
        withBuildCache().succeeds("assertCacheNotCleanedUpYet", "-PlastCleanupCheck=$lastCleanupCheck")
        then:
        // build cache has been cleaned up now
        gcFile().lastModified() >= lastCleanupCheck
    }

    long markCacheForCleanup() {
        gcFile().touch()
        gcFile().lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60)
        return gcFile().lastModified()
    }

    TestFile createOldTrashFile() {
        def trashFile = cacheDir.file("0" * 32).createFile()
        trashFile.lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_CACHE_AGE) * 2
        return trashFile
    }

    private ExecutionResult run() {
        withBuildCache().succeeds("cacheable")
    }
}
