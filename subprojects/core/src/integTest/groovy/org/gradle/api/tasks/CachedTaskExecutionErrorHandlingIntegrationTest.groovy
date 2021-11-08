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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile

class CachedTaskExecutionErrorHandlingIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def setup() {
        settingsFile << """
            class FailingBuildCache extends AbstractBuildCache {
                String shouldFail
            }

            class FailingBuildCacheServiceFactory implements BuildCacheServiceFactory<FailingBuildCache> {
                FailingBuildCacheService createBuildCacheService(FailingBuildCache configuration, Describer describer) {
                    return new FailingBuildCacheService(configuration.shouldFail)
                }
            }

            class FailingBuildCacheService implements BuildCacheService {
                String shouldFail

                FailingBuildCacheService(String shouldFail) {
                    this.shouldFail = shouldFail
                }

                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    println "> Attempting load for \$key"
                    if (shouldFail == "load") {
                        shouldFail = null
                        throw new BuildCacheException("Unable to read " + key)
                    } else {
                        return false
                    }
                }

                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                    println "> Attempting store for \$key"
                    if (shouldFail == "store") {
                        shouldFail = null
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
                    shouldFail = gradle.startParameter.systemPropertiesArgs.get("failOn")
                    push = true
                }
            }
        """

        executer.beforeExecute {
            executer.withBuildCacheEnabled()
        }
    }

    def "remote cache #failEvent error stack trace is printed when requested (#showStacktrace)"() {
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
        succeeds "customTask", "-DfailOn=$failEvent"
        then:
        output.count("> Attempting $failEvent") == 1
        output.count("Could not $failEvent entry") == 1
        output.count("The remote build cache was disabled during the build due to errors.") == 1
        if (expectStacktrace) {
            assert stackTraceContains(DefaultBuildCacheController)
        }

        where:
        failEvent | showStacktrace                     | expectStacktrace
        "load"    | ShowStacktrace.INTERNAL_EXCEPTIONS | false
        "load"    | ShowStacktrace.ALWAYS              | true
        "load"    | ShowStacktrace.ALWAYS_FULL         | true
        "store"   | ShowStacktrace.INTERNAL_EXCEPTIONS | false
        "store"   | ShowStacktrace.ALWAYS              | true
        "store"   | ShowStacktrace.ALWAYS_FULL         | true
    }

    @ToBeFixedForConfigurationCache(because = "FailingBuildCache has not been registered.")
    def "remote cache is disabled after first #failEvent error for the current build"() {
        // Need to do it like this because stacktraces are always enabled for integration tests
        settingsFile << """
            gradle.startParameter.setShowStacktrace(org.gradle.api.logging.configuration.ShowStacktrace.INTERNAL_EXCEPTIONS)
        """

        buildFile << """
            task firstTask {
                outputs.cacheIf { true }
                def outTxt = file("build/first.txt")
                outputs.file(outTxt)
                doLast {
                    outTxt.text = "Done"
                }
            }

            task secondTask {
                dependsOn firstTask
                outputs.cacheIf { true }
                def outTxt = file("build/second.txt")
                outputs.file(outTxt)
                doLast {
                    outTxt.text = "Done"
                }
            }
        """

        executer.withStackTraceChecksDisabled()

        when:
        succeeds "secondTask", "-DfailOn=$failEvent"
        then:
        attemptsBeforeFailure.each { event, count ->
            assert output.count("> Attempting $failEvent") == count
        }
        output.count("Could not $failEvent entry") == 1
        output.count("The remote build cache was disabled during the build due to errors.") == 1

        when:
        cleanBuildDir()
        succeeds "secondTask"

        then: "build cache is still enabled during next build"
        !output.contains("The remote build cache was disabled during the build")
        output.count("> Attempting load") == 2
        output.count("> Attempting store") == 2
        noneSkipped()

        where:
        failEvent | attemptsBeforeFailure
        "load"    | ["load": 1]
        "store"   | ["load": 1 , "store": 1]
    }

    boolean stackTraceContains(Class<?> type) {
        output.contains("\tat ${type.name}.")
    }

    private TestFile cleanBuildDir() {
        file("build").assertIsDir().deleteDir()
    }
}
