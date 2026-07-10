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

class ConfigurationCacheGradlePropertiesFileIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

    def "reuses cache when unused #kind property changes on disk"() {
        given:
        buildFile """
            tasks.register("some")
        """

        when:
        propertiesFile.writeProperties((property): 'one')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties((property): 'two')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateLoaded()

        where:
        kind      | property
        'project' | 'foo'
        'system'  | 'systemProp.foo'
    }

    def "reuses cache when project property changes on disk if used only at execution time via extra container"() {
        given:
        buildFile """
            def props = project.ext
            tasks.register("some") {
                doLast {
                    println("Execution: '\${props.foo}'")
                }
            }
        """

        when:
        propertiesFile.writeProperties(foo: 'one')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties(foo: 'two')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateLoaded()
        outputContains "Execution: 'two'"
    }

    def "invalidates cache when builtin project property changes on disk"() {
        given:
        buildFile """
            tasks.register("some")
        """

        when:
        propertiesFile.writeProperties((property): 'one')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties((property): 'two')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        and:
        outputContains "configuration cache cannot be reused because Gradle property '$property' has changed."

        where:
        property << ['version', 'group', 'status', 'buildDir', 'description']
    }

    def "invalidates cache when project property used at configuration time changes on disk"() {
        given:
        buildFile """
            def access = project.foo
            println("Access: '\${access}'")
            tasks.register("some")
        """

        when:
        propertiesFile.writeProperties(foo: 'one')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties(foo: 'two')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        and:
        outputContains "configuration cache cannot be reused because Gradle property 'foo' has changed."
    }

    def "invalidates cache when properties file with property used at configuration time is added"() {
        given:
        buildFile """
            def access = project.hasProperty('foo')
            println("Access: '\${access}'")
            tasks.register("some")
        """

        when:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties(foo: 'one')
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()
        outputContains "configuration cache cannot be reused because Gradle property 'foo' has changed."

        when: 'file is removed, cache is invalidated'
        propertiesFile.delete()

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()
        outputContains "configuration cache cannot be reused because Gradle property 'foo' has changed."
    }

    def "reuses cache when properties file with unused property is added"() {
        given:
        buildFile """
            def access = project.hasProperty('foo')
            println("Access: '\${access}'")
            tasks.register("some")
        """

        when:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties(bar: 'one')
        configurationCacheRun "some"

        then:
        configurationCache.assertStateLoaded()

        when: 'file is removed, cache is reused'
        propertiesFile.delete()

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateLoaded()
    }

    def "reuses cache when system property used at execution time changes on disk"() {
        given:
        buildFile """
            tasks.register("some") {
                doLast {
                    def access = System.getProperty('foo')
                    println("Access: '\${access}'")
                }
            }
        """

        when:
        propertiesFile.writeProperties('systemProp.foo': 'one')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties('systemProp.foo': 'two')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateLoaded()

        and:
        outputContains "Access: 'two'"
    }

    def "invalidates cache when system property used at configuration time changes on disk"() {
        given:
        buildFile """
            def access = System.getProperty('foo')
            println("Access: '\${access}'")
            tasks.register("some")
        """

        when:
        propertiesFile.writeProperties('systemProp.foo': 'one')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties('systemProp.foo': 'two')

        and:
        configurationCacheRun "some"

        then:
        configurationCache.assertStateStored()

        and:
        outputContains "configuration cache cannot be reused because system property 'foo' has changed."
    }
}
