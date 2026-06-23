/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import spock.lang.Issue

class ConfigurationCacheLoggingListenerIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/30771")
    def "standard error listener added to task logging at configuration time is invoked on configuration cache hit"() {
        given:
        buildFile << """
            tasks.register("logError") {
                def listenerOutput = file("listener-output.txt")
                logging.addStandardErrorListener({ CharSequence line ->
                    listenerOutput << line
                } as StandardOutputListener)
                doLast {
                    logger.error("error-from-task")
                }
            }
        """
        def configurationCache = newConfigurationCacheFixture()
        def listenerOutput = file("listener-output.txt")

        when: "configuration is stored"
        configurationCacheRun("logError")

        then: "the listener registered during configuration receives the task error output"
        configurationCache.assertStateStored()
        listenerOutput.isFile()
        listenerOutput.text.contains("error-from-task")

        when: "configuration is loaded from the cache"
        listenerOutput.delete()
        configurationCacheRun("logError")

        then: "the listener is still invoked on the cache-hit run"
        configurationCache.assertStateLoaded()
        listenerOutput.isFile()
        listenerOutput.text.contains("error-from-task")
    }

    @Issue("https://github.com/gradle/gradle/issues/30771")
    def "standard output listener added to task logging at configuration time is invoked on configuration cache hit"() {
        given:
        buildFile << """
            tasks.register("logOutput") {
                def listenerOutput = file("listener-output.txt")
                logging.addStandardOutputListener({ CharSequence line ->
                    listenerOutput << line
                } as StandardOutputListener)
                doLast {
                    println("output-from-task")
                }
            }
        """
        def configurationCache = newConfigurationCacheFixture()
        def listenerOutput = file("listener-output.txt")

        when: "configuration is stored"
        configurationCacheRun("logOutput")

        then: "the listener registered during configuration receives the task output"
        configurationCache.assertStateStored()
        listenerOutput.isFile()
        listenerOutput.text.contains("output-from-task")

        when: "configuration is loaded from the cache"
        listenerOutput.delete()
        configurationCacheRun("logOutput")

        then: "the listener is still invoked on the cache-hit run"
        configurationCache.assertStateLoaded()
        listenerOutput.isFile()
        listenerOutput.text.contains("output-from-task")
    }

    @Issue("https://github.com/gradle/gradle/issues/30771")
    def "non-serializable task logging listener is reported as a configuration cache problem"() {
        given:
        buildFile << """
            class BrokenListener implements StandardOutputListener {
                // A definitively non-serializable captured reference.
                Thread thread = Thread.currentThread()
                void onOutput(CharSequence output) { }
            }
            tasks.register("logError") {
                logging.addStandardErrorListener(new BrokenListener())
                doLast {
                    logger.error("error-from-task")
                }
            }
        """

        when: "the listener captures a non-serializable object"
        configurationCacheFails("logError")

        then: "the listener is serialized and the unsupported capture is reported against the listener, not silently dropped"
        problems.assertFailureHasProblems(failure) {
            withProblem("Task `:logError` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'java.lang.Thread', a subtype of 'java.lang.Thread', as these are not supported with the configuration cache.") {
                at(":logError").at("logging listeners")
            }
            problemsWithStackTraceCount = 0
        }
    }
}
