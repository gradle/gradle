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

import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import spock.lang.Issue

class ConfigurationCacheStartParameterIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    private static final String SET_BUILD_CACHE_ENABLED_DEPRECATION = "The StartParameter.setBuildCacheEnabled(boolean) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. " +
        "Use the 'org.gradle.caching' Gradle property to enable or disable the build cache instead. " +
        "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecation_enabling_build_cache_from_build_logic"

    ConfigurationCacheFixture fixture = new ConfigurationCacheFixture(this)

    def "resolved default task names are restored on the build start parameter for a configuration cache hit"() {
        given:
        buildFile """
            abstract class PrintRequestedTasks extends DefaultTask {
                @Inject abstract StartParameter getStartParameter()
                @TaskAction void printIt() {
                    println("REQUESTED=" + getStartParameter().taskNames)
                }
            }

            defaultTasks 'printRequestedTasks'
            tasks.register('printRequestedTasks', PrintRequestedTasks)
        """

        when: "store run with no tasks on the command line, so default tasks apply"
        configurationCacheRun()

        then:
        fixture.assertStateStored()
        outputContains("REQUESTED=[printRequestedTasks]")

        when: "cache hit"
        configurationCacheRun()

        then:
        fixture.assertStateLoaded()
        outputContains("REQUESTED=[printRequestedTasks]")
    }

    @Issue("https://github.com/gradle/gradle/issues/37088")
    def "build cache enablement set programmatically in settings is restored on a configuration cache hit"() {
        given:
        settingsFile """
            gradle.startParameter.buildCacheEnabled = true
        """
        buildFile """
            abstract class PrintBuildCache extends DefaultTask {
                @Inject abstract StartParameter getStartParameter()
                @TaskAction void printIt() {
                    println("BUILD_CACHE_ENABLED=" + getStartParameter().buildCacheEnabled)
                }
            }
            tasks.register('printBuildCache', PrintBuildCache)
        """

        when: "store run: settings script runs and enables the build cache"
        executer.expectDocumentedDeprecationWarning(SET_BUILD_CACHE_ENABLED_DEPRECATION)
        configurationCacheRun("printBuildCache")

        then:
        fixture.assertStateStored()
        outputContains("BUILD_CACHE_ENABLED=true")

        when: "cache hit: settings do not run"
        configurationCacheRun("printBuildCache")

        then:
        fixture.assertStateLoaded()
        outputContains("BUILD_CACHE_ENABLED=true")
    }

    @Issue("https://github.com/gradle/gradle/issues/37088")
    def "build cache enabled programmatically in settings still caches task outputs on a configuration cache hit"() {
        given: "the build cache is enabled programmatically, not via the command line"
        executer.requireOwnGradleUserHomeDir()
        def cache = new TestBuildCache(file("cache-dir"))
        settingsFile(cache.localCacheConfiguration() + """
            gradle.startParameter.buildCacheEnabled = true
        """)
        cacheableTask()

        when: "store run populates the build cache"
        executer.expectDocumentedDeprecationWarning(SET_BUILD_CACHE_ENABLED_DEPRECATION)
        configurationCacheRun("customTask")

        then: "the task runs and its output is stored in the cache"
        fixture.assertStateStored()
        !cache.empty

        when: "the output is removed and the build is rerun as a configuration cache hit"
        file("build").forceDeleteDir()
        configurationCacheRun("customTask")

        then: "the task output is loaded from the cache, proving build caching is active without reconfiguration"
        fixture.assertStateLoaded()
        result.groupedOutput.task(":customTask").outcome == "FROM-CACHE"
    }

    @Issue("https://github.com/gradle/gradle/issues/37088")
    def "build cache enabled only via --build-cache is not restored on a later invocation without it"() {
        given: "the build cache is not enabled by build logic, only available via the command line"
        executer.requireOwnGradleUserHomeDir()
        def cache = new TestBuildCache(file("cache-dir"))
        settingsFile(cache.localCacheConfiguration())
        cacheableTask()

        when: "store run with --build-cache populates the cache"
        configurationCacheRun("customTask", "--build-cache")

        then:
        fixture.assertStateStored()
        !cache.empty

        when: "the output is removed and the build is rerun as a hit without --build-cache"
        file("build").forceDeleteDir()
        configurationCacheRun("customTask")

        then: "the current invocation wins: the build cache is off, so the task executes instead of loading from cache"
        fixture.assertStateLoaded()
        result.groupedOutput.task(":customTask").outcome != "FROM-CACHE"
    }

    @Issue("https://github.com/gradle/gradle/issues/37088")
    def "build cache enabled only via the org.gradle.caching property is not restored on a later invocation that disables it"() {
        given: "the build cache is enabled by a Gradle property, not by build logic"
        executer.requireOwnGradleUserHomeDir()
        def cache = new TestBuildCache(file("cache-dir"))
        settingsFile(cache.localCacheConfiguration())
        file("gradle.properties") << "org.gradle.caching=true"
        cacheableTask()

        when: "store run enabled by the property populates the cache"
        configurationCacheRun("customTask")

        then:
        fixture.assertStateStored()
        !cache.empty

        when: "the output is removed and the build is rerun as a hit, disabling the cache via --no-build-cache"
        // The property is unchanged (so the entry is reused) but the command line overrides it this run.
        file("build").forceDeleteDir()
        configurationCacheRun("customTask", "--no-build-cache")

        then: "the current invocation wins: the build cache is off, so the task executes instead of loading from cache"
        fixture.assertStateLoaded()
        result.groupedOutput.task(":customTask").outcome != "FROM-CACHE"
    }

    private void cacheableTask() {
        buildFile """
            @CacheableTask
            abstract class CustomTask extends DefaultTask {
                @Input String content = "content"
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction void run() { outputFile.get().asFile.text = content }
            }
            tasks.register('customTask', CustomTask) {
                outputFile = layout.buildDirectory.file("out.txt")
            }
        """
    }
}
