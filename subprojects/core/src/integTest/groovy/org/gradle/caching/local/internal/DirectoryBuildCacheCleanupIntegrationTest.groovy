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
import org.gradle.integtests.fixtures.LocalBuildCacheFixture

import java.util.concurrent.TimeUnit

class DirectoryBuildCacheCleanupIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {
    def setup() {
        settingsFile << """
            buildCache {
                local {
                    targetSizeInMB = 10
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
            
            task cacheable(type: CustomTask)
        """
    }

    def "does not cleanup on every build"() {
        when:
        runMultiple(20)
        then:
        def timeNow = System.currentTimeMillis()
        def originalList = listCacheFiles()
        originalList.size() == 20
        originalList.eachWithIndex { cacheEntry, index ->
            // Set the modtime for each cache entry back monotonically increasing days
            // so the first cache entry was accessed now-0 days
            // the next now-1 days, etc.
            cacheEntry.lastModified = timeNow - TimeUnit.DAYS.toMillis(index)
        }
        // build cache hasn't been cleaned yet

        when:
        cleanupBuildCacheNow()
        withBuildCache().succeeds("cacheable", "-i")
        then:
        def newList = listCacheFiles()
        newList.size() == 9
        newList.each { cacheEntry ->
            // All of the old cache entries should have been deleted first
            assert (cacheEntry.lastModified() - timeNow) < TimeUnit.DAYS.toMillis(newList.size())
        }
    }

    void runMultiple(int times) {
        (1..times).each {
            withBuildCache().succeeds("cacheable", "-Prun=${it}")
        }
    }
}
