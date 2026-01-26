/*
 * Copyright 2020 the original author or authors.
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

class ConfigurationCacheMultiProjectIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "reuses cache for absolute task invocation from subproject dir across dirs"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """
        buildFile """
            task ok
        """
        def a = createDir('a')
        def b = createDir('b')
        def configurationCache = newConfigurationCacheFixture()

        when:
        inDirectory a
        configurationCacheRun ':ok'

        then:
        configurationCache.assertStateStored()

        when:
        inDirectory b
        configurationCacheRun ':ok'

        then:
        configurationCache.assertStateLoaded()

        when:
        inDirectory a
        configurationCacheRun ':ok'

        then:
        configurationCache.assertStateLoaded()
    }

    def "reuses cache for relative task invocation from subproject dir"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """
        buildFile """
            allprojects {
                task ok
            }
        """
        def a = createDir('a')
        def b = createDir('b')
        def configurationCache = newConfigurationCacheFixture()

        when:
        inDirectory testDirectory
        configurationCacheRun 'ok'

        then:
        result.assertTasksScheduled(':ok', ':a:ok', ':b:ok')
        configurationCache.assertStateStored()

        when:
        inDirectory a
        configurationCacheRun 'ok'

        then:
        result.assertTasksScheduled(':a:ok')
        configurationCache.assertStateStored()

        when:
        inDirectory b
        configurationCacheRun 'ok'

        then:
        result.assertTasksScheduled(':b:ok')
        configurationCache.assertStateStored()

        when:
        inDirectory a
        configurationCacheRun 'ok'

        then:
        result.assertTasksScheduled(':a:ok')
        configurationCache.assertStateLoaded()

        when:
        inDirectory b
        configurationCacheRun 'ok'

        then:
        result.assertTasksScheduled(':b:ok')
        configurationCache.assertStateLoaded()

        when:
        inDirectory testDirectory
        configurationCacheRun 'ok'

        then:
        result.assertTasksScheduled(':ok', ':a:ok', ':b:ok')
        configurationCache.assertStateLoaded()
    }

    def "can store configuration cache with multiple projects using ValueSources in parallel"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """

        // Create a ValueSource that will write to build-scoped fingerprints
        buildFile """
            import org.gradle.api.provider.ValueSource
            import org.gradle.api.provider.ValueSourceParameters

            abstract class EnvVarValueSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override
                String obtain() {
                    return System.getenv("TEST_VAR") ?: "default"
                }
            }

            allprojects {
                task myTask {
                    def envValue = providers.of(EnvVarValueSource) {}
                    doLast {
                        println "Task \${path} with value: \${envValue.get()}"
                    }
                }
            }
        """

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'myTask', '--parallel'

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(':myTask', ':a:myTask', ':b:myTask')

        when:
        configurationCacheRun 'myTask', '--parallel'

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(':myTask', ':a:myTask', ':b:myTask')
    }
}
