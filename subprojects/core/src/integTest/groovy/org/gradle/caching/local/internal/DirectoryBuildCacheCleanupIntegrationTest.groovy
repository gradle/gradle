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
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Ignore
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class DirectoryBuildCacheCleanupIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    private final static int MAX_CACHE_SIZE = 5 // MB
    def setup() {
        settingsFile << """
            buildCache {
                local {
                    targetSizeInMB = ${MAX_CACHE_SIZE}
                }
            }
        """
        def bytes = new byte[1024*1024]
        new Random().nextBytes(bytes)
        file("output.txt").bytes = bytes

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
            
            task assertBuildCacheOverTarget {
                doLast {
                    def cacheSize = file("${cacheDir.toURI()}").listFiles().collect { it.length() }.sum()
                    long cacheSizeInMB = cacheSize / 1024 / 1024
                    assert cacheSizeInMB >= ${MAX_CACHE_SIZE}
                }
            }
        """
    }

    def "cleans up when over target"() {
        when:
        runMultiple(MAX_CACHE_SIZE*2)
        then:
        def originalList = listCacheFiles()
        // build cache hasn't been cleaned yet
        originalList.size() == MAX_CACHE_SIZE*2
        calculateCacheSize(originalList) >= MAX_CACHE_SIZE

        when:
        cleanupBuildCacheNow()
        and:
        withBuildCache().succeeds("cacheable")
        then:
        def newList = listCacheFiles()
        newList.size() == MAX_CACHE_SIZE-1
        // Cache should be under the target size
        calculateCacheSize(newList) <= MAX_CACHE_SIZE
    }

    def "cleans up the oldest entries first"() {
        when:
        runMultiple(MAX_CACHE_SIZE*2)
        then:
        def originalList = listCacheFiles()
        // build cache hasn't been cleaned yet
        originalList.size() == MAX_CACHE_SIZE*2
        calculateCacheSize(originalList) >= MAX_CACHE_SIZE

        when:
        cleanupBuildCacheNow()
        def timeNow = System.currentTimeMillis()
        originalList.eachWithIndex { cacheEntry, index ->
            // Set the lastModified time for each cache entry back monotonically increasing days
            // so the first cache entry was accessed now-0 days
            // the next now-1 days, etc.
            cacheEntry.lastModified = timeNow - TimeUnit.DAYS.toMillis(index)
        }
        and:
        withBuildCache().succeeds("cacheable")
        then:
        def newList = listCacheFiles()
        newList.size() == MAX_CACHE_SIZE-1

        // All of the old cache entries should have been deleted first
        def ageOfCacheEntries = newList.collect { cacheEntry ->
            (cacheEntry.lastModified() - timeNow)
        }
        def oldestCacheEntry = TimeUnit.DAYS.toMillis(newList.size())
        ageOfCacheEntries.every { it < oldestCacheEntry }

        // Cache should be under the target size
        calculateCacheSize(newList) <= MAX_CACHE_SIZE
    }

    def "cleans up based on LRU"() {
        when:
        runMultiple(MAX_CACHE_SIZE*2)
        then:
        def originalList = listCacheFiles()
        // build cache hasn't been cleaned yet
        originalList.size() == MAX_CACHE_SIZE*2
        calculateCacheSize(originalList) >= MAX_CACHE_SIZE

        when:
        def oldTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(100)
        originalList.each { cacheEntry ->
            cacheEntry.lastModified = oldTime
        }
        and:
        withBuildCache().succeeds("cacheable", "-Prun=2")
        withBuildCache().succeeds("cacheable", "-Prun=4")
        withBuildCache().succeeds("cacheable", "-Prun=6")
        then:
        def recentlyUsed = originalList.findAll {
            it.lastModified() > oldTime
        }
        recentlyUsed.size() == 3

        when:
        cleanupBuildCacheNow()
        and:
        withBuildCache().succeeds("cacheable")

        then:
        def newList = listCacheFiles()
        newList.size() == 4
        newList.containsAll(recentlyUsed)
    }

    def "does not cleanup on every build"() {
        when:
        runMultiple(MAX_CACHE_SIZE*2)
        then:
        def originalList = listCacheFiles()
        // build cache hasn't been cleaned yet
        originalList.size() == MAX_CACHE_SIZE*2
        calculateCacheSize(originalList) >= MAX_CACHE_SIZE

        when:
        cleanupBuildCacheNow()
        and:
        withBuildCache().succeeds("cacheable")
        then:
        def newList = listCacheFiles()
        newList.size() == MAX_CACHE_SIZE-1
        // Cache should be under the target size
        calculateCacheSize(newList) <= MAX_CACHE_SIZE
        def lastCleanedTime = gcFile().lastModified()

        // build cache shouldn't clean up again
        when:
        runMultiple(MAX_CACHE_SIZE)
        then:
        // the exact count depends on exactly which cache entries were cleaned above
        // which depends on file system ordering/time resolution
        listCacheFiles().size() >= MAX_CACHE_SIZE
        lastCleanedTime == gcFile().lastModified()
    }

    @Unroll
    def "produces reasonable message when cache is too small (#size)"() {
        settingsFile << """
            buildCache {
                local {
                    targetSizeInMB = ${size}
                }
            }
        """
        expect:
        fails("help")
        result.error.contains("Directory build cache needs to have at least 1 MB of space but more space is useful.")

        where:
        size << [-1, 0]
    }

    def "build cache cleanup is triggered after 7 days"() {
        def messageRegex = /Build cache \(.+\) cleaned up in .+ secs./

        when:
        withBuildCache().succeeds("cacheable")
        then:
        listCacheFiles().size() == 1
        def originalCheckTime = gcFile().lastModified()
        // Set the time back 1 day
        gcFile().setLastModified(originalCheckTime - TimeUnit.DAYS.toMillis(1))
        def lastCleanupCheck = gcFile().lastModified()

        // One day isn't enough to trigger
        when:
        withBuildCache().succeeds("cacheable", "-i")
        then:
        result.output.readLines().every {
            !it.matches(messageRegex)
        }
        gcFile().lastModified() == lastCleanupCheck

        // 7 days is enough to trigger
        when:
        gcFile().setLastModified(originalCheckTime - TimeUnit.DAYS.toMillis(7))
        and:
        withBuildCache().succeeds("cacheable", "-i")
        then:
        result.output.readLines().any {
            it.matches(messageRegex)
        }
        gcFile().lastModified() > lastCleanupCheck

        // More than 7 days is enough to trigger
        when:
        gcFile().setLastModified(originalCheckTime - TimeUnit.DAYS.toMillis(100))
        and:
        withBuildCache().succeeds("cacheable", "-i")
        then:
        result.output.readLines().any {
            it.matches(messageRegex)
        }
        gcFile().lastModified() > lastCleanupCheck
    }

    @Ignore("buildSrc closes the build cache after building")
    def "buildSrc does not try to clean build cache"() {
        // Copy cache configuration
        file("buildSrc/settings.gradle").text = settingsFile.text
        when:
        runMultiple(MAX_CACHE_SIZE*2)
        and:
        then:
        // build cache hasn't been cleaned yet
        calculateCacheSize(listCacheFiles()) >= MAX_CACHE_SIZE
        def lastCleanupCheck = gcFile().makeOlder().lastModified()

        when:
        cleanupBuildCacheNow()
        and:
        // During the build, the build cache should be over the target still
        withBuildCache().succeeds("assertBuildCacheOverTarget")
        then:
        // build cache has been cleaned up now
        calculateCacheSize(listCacheFiles()) <= MAX_CACHE_SIZE
        gcFile().lastModified() >= lastCleanupCheck
    }

    def "composite builds do not try to clean build cache"() {
        file("included/build.gradle") << """
            apply plugin: 'java'
            group = "com.example"
            version = "2.0"
        """
        // Copy cache configuration
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
            assertBuildCacheOverTarget {
                dependsOn configurations.test
                doFirst {
                    println configurations.test.files
                }
            }
        """
        when:
        runMultiple(MAX_CACHE_SIZE*2)
        and:
        then:
        // build cache hasn't been cleaned yet
        calculateCacheSize(listCacheFiles()) >= MAX_CACHE_SIZE
        def lastCleanupCheck = gcFile().makeOlder().lastModified()

        when:
        cleanupBuildCacheNow()
        and:
        // During the build, the build cache should be over the target still (the composite didn't clean it up)
        withBuildCache().succeeds("assertBuildCacheOverTarget")
        then:
        // build cache has been cleaned up now
        calculateCacheSize(listCacheFiles()) <= MAX_CACHE_SIZE
        gcFile().lastModified() >= lastCleanupCheck
    }

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
            assertBuildCacheOverTarget.dependsOn gradleBuild
        """
        when:
        runMultiple(MAX_CACHE_SIZE*2)
        and:
        then:
        // build cache hasn't been cleaned yet
        calculateCacheSize(listCacheFiles()) >= MAX_CACHE_SIZE
        def lastCleanupCheck = gcFile().makeOlder().lastModified()

        when:
        cleanupBuildCacheNow()
        and:
        // During the build, the build cache should be over the target still
        withBuildCache().succeeds("assertBuildCacheOverTarget")
        then:
        // build cache has been cleaned up now
        calculateCacheSize(listCacheFiles()) <= MAX_CACHE_SIZE
        gcFile().lastModified() >= lastCleanupCheck
    }

    private static long calculateCacheSize(List<TestFile> originalList) {
        def cacheSize = originalList.collect { it.length() }.sum()
        cacheSize / 1024 / 1024
    }

    void runMultiple(int times) {
        (1..times).each {
            withBuildCache().succeeds("cacheable", "-Prun=${it}")
        }
    }
}
