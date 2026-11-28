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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

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

    @Requires([
        IntegTestPreconditions.NotEmbeddedExecutor,
        IntegTestPreconditions.Java17HomeAvailable,
        IntegTestPreconditions.Java21HomeAvailable
    ])
    def "changing '#property' #effect CC"() {
        given:
        settingsFile.touch()

        when:
        propertiesFile.writeProperties((property): before.toString())

        and:
        configurationCacheRun 'help'

        then:
        configurationCache.assertStateStored()

        when:
        propertiesFile.writeProperties((property): after.toString())

        and:
        configurationCacheRun 'help'

        then:
        if (invalidates) {
            configurationCache.assertStateStored()
        } else {
            configurationCache.assertStateLoaded()
        }

        where:
        property                              | before     | after       | invalidates
        'org.gradle.caching'                  | true       | false       | false
        'org.gradle.caching.debug'            | true       | false       | false

        // <NA>
        // org.gradle.configuration-cache=(true,false)
        // </NA>

        'org.gradle.configureondemand'        | true       | false       | false
        'org.gradle.console'                  | 'auto'     | 'plain'     | false

        // <NA>
        // org.gradle.continue=(true,false)
        // </NA>

        'org.gradle.daemon'                   | true       | false       | false
        'org.gradle.daemon.idletimeout'       | 1000       | 2000        | false

        // <Hard to test>
        // org.gradle.debug=(true,false)
        // </Hard to test>

        'org.gradle.java.home'                | java17()   | java21()    | true

        // <Requires toolchains / tested separately>
        // org.gradle.java.installations.auto-detect=(true,false)
        // org.gradle.java.installations.auto-download=(true,false)
        // org.gradle.java.installations.paths=(list of JDK installations)
        // org.gradle.java.installations.fromEnv=(list of environment variables)
        // </Requires toolchains / tested separately>

        'org.gradle.jvmargs'                  | '-Xmx256m' | '-Xmx512m'  | false
        'org.gradle.logging.level'            | 'warn'     | 'lifecycle' | false
        'org.gradle.parallel'                 | true       | false       | false
        'org.gradle.priority'                 | 'low'      | 'normal'    | false
        'org.gradle.projectcachedir'          | '.gradle'  | '.custom'   | true
        'org.gradle.problems.report'          | true       | false       | false
        'org.gradle.unsafe.isolated-projects' | true       | false       | true
        'org.gradle.vfs.verbose'              | true       | false       | false
        'org.gradle.vfs.watch'                | true       | false       | false
        'org.gradle.warning.mode'             | 'all'      | 'fail'      | false
        'org.gradle.workers.max'              | 2          | 4           | false

        effect = invalidates ? 'invalidates' : 'does not invalidate'
    }

    private String java21() {
        AvailableJavaHomes.getJdk21().javaHome.absolutePath
    }

    private String java17() {
        AvailableJavaHomes.getJdk17().javaHome.absolutePath
    }
}
