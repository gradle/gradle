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

import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.caching.internal.controller.DefaultBuildCacheController
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class CachedTaskExecutionErrorHandlingIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def setup() {
        settingsFile << """
            class FailingBuildCache extends AbstractBuildCache {
                boolean shouldFail
            }
            
            class FailingBuildCacheServiceFactory implements BuildCacheServiceFactory<FailingBuildCache> {
                FailingBuildCacheService createBuildCacheService(FailingBuildCache configuration, Describer describer) {
                    return new FailingBuildCacheService(configuration.shouldFail)
                }
            }
            
            class FailingBuildCacheService implements BuildCacheService {
                boolean shouldFail
                boolean recoverable
                
                FailingBuildCacheService(boolean shouldFail) {
                    this.shouldFail = shouldFail
                }
                
                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    if (shouldFail) {
                        throw new BuildCacheException("Unable to read " + key)
                    } else {
                        return false
                    }
                }
    
                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                    if (shouldFail) {
                        throw new BuildCacheException("Unable to write " + key)
                    }
                }
    
                @Override
                void close() throws IOException {
                }
            }
            
            buildCache {
                registerBuildCacheService(FailingBuildCache, FailingBuildCacheServiceFactory)
                
                local {
                    enabled = false
                }
                
                remote(FailingBuildCache) {
                    shouldFail = gradle.startParameter.systemPropertiesArgs.containsKey("fail")
                    push = true
                }
            }
        """

        executer.withBuildCacheEnabled()
    }

    @Unroll
    def "cache switches off after first error for the current build (stacktraces: #showStacktrace)"() {
        // Need to do it like this because stacktraces are always enabled for integration tests
        settingsFile << """
            gradle.startParameter.setShowStacktrace(org.gradle.api.logging.configuration.ShowStacktrace.$showStacktrace)
        """

        buildFile << """
            task customTask {
                outputs.cacheIf { true }
                outputs.file("build/output.txt")
                doLast {
                    file("build/output.txt").text = "Done"
                }
            }
        """

        if (expectStacktrace) {
            executer.withStackTraceChecksDisabled()
        }

        when:
        succeeds "customTask", "-Dfail"
        then:
        output.count("Could not load entry") == 1
        output.count("Could not store entry") == 0
        output.count("Failure while packing") == 0
        output.count("The remote build cache was disabled during the build due to errors.") == 1
        if (expectStacktrace) {
            assert stackTraceContains(DefaultBuildCacheController)
        }

        when:
        cleanBuildDir()
        succeeds "customTask"

        then: "build cache is still enabled during next build"
        !output.contains("The remote build cache is now disabled")
        !output.contains("The remote build cache was disabled during the build")
        if (expectStacktrace) {
            assert !stackTraceContains(DefaultBuildCacheController)
        }
        skippedTasks.empty

        where:
        showStacktrace                     | expectStacktrace
        ShowStacktrace.INTERNAL_EXCEPTIONS | false
        ShowStacktrace.ALWAYS              | true
        ShowStacktrace.ALWAYS_FULL         | true
    }

    boolean stackTraceContains(Class<?> type) {
        output.contains("\tat ${type.name}.")
    }

    private TestFile cleanBuildDir() {
        file("build").assertIsDir().deleteDir()
    }
}
