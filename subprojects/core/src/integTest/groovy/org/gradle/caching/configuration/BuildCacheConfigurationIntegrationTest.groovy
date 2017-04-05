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

package org.gradle.caching.configuration

import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.launcher.daemon.configuration.GradleProperties
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Unroll

class BuildCacheConfigurationIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            task assertLocalCacheConfigured {
                doLast {
                    assert gradle.services.get(BuildCacheConfiguration).local.directory == "expected"
                }
            }
        """
    }

    def "can configure with settings.gradle"() {
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = "expected"
                }
            }
        """
        expect:
        succeeds("assertLocalCacheConfigured")
    }

    def "can configure with init script"() {
        def initScript = file("initBuildCache.gradle") << """
            gradle.settingsEvaluated { settings ->
                settings.buildCache {
                    local(DirectoryBuildCache) {
                        directory = "expected"
                    }
                }
            }
        """
        expect:
        executer.usingInitScript(initScript)
        succeeds("assertLocalCacheConfigured")
    }

    def "configuration in init script wins over settings.gradle"() {
        def initScript = file("initBuildCache.gradle") << """
            gradle.settingsEvaluated { settings ->
                settings.buildCache {
                    local(DirectoryBuildCache) {
                        directory = "expected"
                    }
                }
            }
        """
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = "wrong"
                }
            }
        """
        expect:
        executer.usingInitScript(initScript)
        succeeds("assertLocalCacheConfigured")
    }

    def "buildSrc and project builds configured separately"() {
        def configuration = { path ->
            """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = "$path"
                }
            }
            """
        }
        settingsFile << configuration("expected")
        file("buildSrc/settings.gradle") << configuration("buildSrc-expected")
        file("buildSrc/build.gradle") << """
            apply plugin: 'groovy'

            task assertLocalCacheConfigured {
                doLast {
                    assert gradle.services.get(BuildCacheConfiguration).local.directory == "buildSrc-expected"
                }
            }
            
            build.dependsOn assertLocalCacheConfigured
        """
        expect:
        succeeds("assertLocalCacheConfigured")
    }

    @Unroll
    def "last #cache cache configuration wins"() {
        settingsFile << """
            class CustomBuildCache extends AbstractBuildCache {}
            class AnotherBuildCache extends AbstractBuildCache {}
            
            buildCache {
                $cache(CustomBuildCache)
                $cache(AnotherBuildCache)
            }
            
            assert buildCache.$cache instanceof AnotherBuildCache
        """
        expect:
        succeeds("help")

        where:
        cache << ["local", "remote"]
    }

    def "disables remote cache with --offline"() {
        settingsFile << """
            class CustomBuildCache extends AbstractBuildCache {}
            
            buildCache {
                remote(CustomBuildCache)
            }            
        """
        expect:
        succeeds("help", "--build-cache", "--offline")
        result.output.contains("Remote build cache is disabled when running with --offline.")
    }

    def "system properties still have an effect on pushing and pulling"() {
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = file("local-cache")
                }
            }
        """
        when:
        executer.withBuildCacheEnabled()
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        executer.withArgument("-D${DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PUSH}=false")
        succeeds("tasks")
        then:
        result.assertOutputContains "Using directory (${file("local-cache")}) as local build cache, push is enabled."
        result.assertOutputContains "Pushing to any build cache is globally disabled."
        when:
        executer.withBuildCacheEnabled()
        executer.expectDeprecationWarning()
        executer.withArgument("-D${DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PULL}=false")
        succeeds("tasks")
        then:
        result.assertOutputContains("Using directory (${file("local-cache")}) as local build cache, push is enabled.")
        result.assertOutputContains "Pulling from any build cache is globally disabled."
        when:
        executer.withBuildCacheEnabled()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.withArgument("-D${DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PULL}=false")
        executer.withArgument("-D${DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PUSH}=false")
        succeeds("tasks")
        then:
        result.assertOutputContains("Using directory (${file("local-cache")}) as local build cache, push is enabled.")
        result.assertOutputContains "Pushing to any build cache is globally disabled."
        result.assertOutputContains "Pulling from any build cache is globally disabled."
    }

    @IgnoreIf({GradleContextualExecuter.embedded})
    @Ignore("Must fix for 4.0")
    def "emits a useful deprecation message when using the old build cache system property"() {
        when:
        executer.expectDeprecationWarning()
        executer.withArgument("-D${GradleProperties.TASK_OUTPUT_CACHE_PROPERTY}=true")
        succeeds("tasks")
        then:
        result.assertOutputContains("The org.gradle.cache.tasks property has been deprecated and is scheduled to be removed in Gradle 4.0. Use org.gradle.caching instead.")
    }

    def "emits a useful incubating message when using the build cache"() {
        when:
        executer.withBuildCacheEnabled()
        succeeds("tasks")
        then:
        result.assertOutputContains("Using directory")
    }

    def "command-line --no-build-cache wins over system property"() {
        file("gradle.properties") << """
            org.gradle.caching=true
        """
        executer.withArgument("--no-build-cache")
        when:
        succeeds("tasks")
        then:
        !result.output.contains("Using directory")
    }

    def "command-line --build-cache wins over system property"() {
        file("gradle.properties") << """
            org.gradle.caching=false
        """
        executer.withArgument("--build-cache")
        when:
        succeeds("tasks")
        then:
        result.assertOutputContains("Using directory")
    }

    def "does not use the build cache when it is not enabled"() {
        given:
        buildFile << customTaskCode()
        when:
        // Disable the local build cache
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = "local-cache"
                    enabled = false
                }
            }
        """
        executer.withBuildCacheEnabled()
        succeeds("customTask")
        then:
        result.assertOutputContains("Task output caching is enabled, but no build caches are configured or enabled.")
        and:
        file("local-cache").assertIsEmptyDir()
    }

    def "does not populate the build cache when we cannot push to it"() {
        given:
        buildFile << customTaskCode()
        when:
        // Disable pushing to the local build cache
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = file("local-cache")
                    push = false
                }
            }
        """
        executer.withBuildCacheEnabled()
        succeeds("customTask")
        then:
        result.assertOutputContains("Using directory (${file("local-cache")}) as local build cache, push is disabled")
        and:
        !file("local-cache").listFiles().any { it.name ==~ /\p{XDigit}{32}/}
    }

    private static String customTaskCode() {
        """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputFile
                File outputFile = new File(temporaryDir, "output.txt")

                @TaskAction
                void generate() {
                    outputFile.text = "done"
                }
            }

            task customTask(type: CustomTask)
        """
    }
}
