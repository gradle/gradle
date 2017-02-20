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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

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
                local {
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
                    local {
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
                    local {
                        directory = "expected"
                    }
                }
            }
        """
        settingsFile << """
            buildCache {
                local {
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
                local {
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

    def "last remote cache configuration wins"() {
        settingsFile << """
            class CustomBuildCache extends AbstractBuildCache {}
            class AnotherBuildCache extends AbstractBuildCache {}
            
            buildCache {
                remote(CustomBuildCache)
                remote(AnotherBuildCache)
            }
            
            assert buildCache.remote instanceof AnotherBuildCache
        """
        expect:
        succeeds("help")
    }

    def "system properties still have an effect on pushing and pulling"() {
        when:
        executer.withBuildCacheEnabled()
        executer.withArgument("-Dorg.gradle.cache.tasks.push=false")
        succeeds("tasks")
        then:
        result.assertOutputContains("Retrieving task output from a local build cache")
        when:
        executer.withBuildCacheEnabled()
        executer.withArgument("-Dorg.gradle.cache.tasks.pull=false")
        succeeds("tasks")
        then:
        result.assertOutputContains("Pushing task output to a local build cache")
        when:
        executer.withBuildCacheEnabled()
        executer.withArgument("-Dorg.gradle.cache.tasks.pull=false")
        executer.withArgument("-Dorg.gradle.cache.tasks.push=false")
        succeeds("tasks")
        then:
        result.assertOutputContains("No build caches are allowed to push or pull task outputs, but task output caching is enabled.")
    }

    def "emits a useful incubating message when using the build cache"() {
        when:
        executer.withBuildCacheEnabled()
        succeeds("tasks")
        then:
        result.assertOutputContains("Using a local build cache")
    }

    def "does not use the build cache when it is not enabled"() {
        given:
        buildFile << customTaskCode()
        when:
        // Disable the local build cache
        settingsFile << """
            buildCache {
                local {
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
                local {
                    directory = file("local-cache")
                    push = false
                }
            }
        """
        executer.withBuildCacheEnabled()
        succeeds("customTask")
        then:
        result.assertOutputContains("Retrieving task output from a local build cache")
        and:
        !file("local-cache").listFiles().any { it.name ==~ /\p{XDigit}{32}/}
    }

    private String customTaskCode() {
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
