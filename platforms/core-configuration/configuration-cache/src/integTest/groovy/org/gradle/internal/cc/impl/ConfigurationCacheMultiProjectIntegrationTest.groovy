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

    def "reuses cache when project reads a mutated system property during nested evaluation"() {
        given:
        settingsFile """
            include "a", "b"
        """

        buildFile("a/build.gradle", """
            def captured = System.getProperty('my.prop')
            tasks.register('printProp') {
                doLast { println("my.prop in :a = \${captured}") }
            }
        """)

        buildFile("b/build.gradle", """
            def captured = System.setProperty('my.prop', 'mutated')
            tasks.register('printProp') {
                // Forces nested evaluation of `:a`
                dependsOn(':a:printProp')
                doLast {
                    println("my.prop in :b before mutation = \${captured}")
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun "--configure-on-demand", ":b:printProp", "-Dmy.prop=original"

        then:
        configurationCache.assertStateStored()
        outputContains("my.prop in :b before mutation = original")
        outputContains("my.prop in :a = mutated")

        when:
        configurationCacheRun "--configure-on-demand", ":b:printProp", "-Dmy.prop=original"

        then:
        configurationCache.assertStateLoaded()
        outputContains("my.prop in :b before mutation = original")
        outputContains("my.prop in :a = mutated")
    }

    def "reuses cache when project reads a system property that the depending project mutates after the nested evaluation"() {
        given:
        settingsFile """
            include "a", "b"
        """

        // 'a' is evaluated from within the evaluation of 'b', before the mutation, so it observes the original value
        buildFile("a/build.gradle", """
            def captured = System.getProperty('my.prop')
            tasks.register('printProp') {
                doLast { println("my.prop in :a = \${captured}") }
            }
        """)

        buildFile("b/build.gradle", """
            evaluationDependsOn(":a") // Forces nested evaluation immediately
            def captured = System.setProperty('my.prop', 'mutated')
            tasks.register('printProp') {
                dependsOn(':a:printProp')
                doLast {
                    println("my.prop in :b before mutation = \${captured}")
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun "--configure-on-demand", ":b:printProp", "-Dmy.prop=original"

        then:
        configurationCache.assertStateStored()
        outputContains("my.prop in :b before mutation = original")
        outputContains("my.prop in :a = original")

        when:
        configurationCacheRun "--configure-on-demand", ":b:printProp", "-Dmy.prop=original"

        then:
        configurationCache.assertStateLoaded()
        outputContains("my.prop in :b before mutation = original")
        outputContains("my.prop in :a = original")
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
}
