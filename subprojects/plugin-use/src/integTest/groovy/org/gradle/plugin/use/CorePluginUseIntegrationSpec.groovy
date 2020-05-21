/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.CoreMatchers.startsWith

class CorePluginUseIntegrationSpec extends AbstractIntegrationSpec {

    public static final String QUALIFIED_JAVA = "org.gradle.java"
    public static final String UNQUALIFIED_JAVA = "java"

    void "can resolve core plugins"() {
        when:
        buildScript """
            plugins {
              id 'java'
            }
        """

        then:
        succeeds "javadoc"
    }

    void "can resolve qualified core plugins"() {
        when:
        buildScript """
            plugins {
              id 'org.gradle.java'
            }
        """

        then:
        succeeds "javadoc"
    }

    void "core plugins cannot have a version number"() {
        given:
        buildScript """
            plugins {
                id "java" version "1.0"
            }
        """

        when:
        fails "help"

        then:
        failure.assertHasDescription("Error resolving plugin [id: 'java', version: '1.0']")
        failure.assertHasCause("Plugin 'java' is a core Gradle plugin, which cannot be specified with a version number")
        failure.assertHasFileName("Build file '$buildFile.absolutePath'")
        failure.assertHasLineNumber(3)
    }

    void "qualified core plugins cannot have a version number"() {
        given:
        buildScript """
            plugins {
                id "org.gradle.java" version "1.0"
            }
        """

        when:
        fails "help"

        then:
        failure.assertHasDescription("Error resolving plugin [id: 'org.gradle.java', version: '1.0']")
        failure.assertHasCause("Plugin 'org.gradle.java' is a core Gradle plugin, which cannot be specified with a version number")
        failure.assertHasFileName("Build file '$buildFile.absolutePath'")
        failure.assertHasLineNumber(3)
    }

    void "core plugins cannot be used with apply false"() {
        given:
        buildScript """
            plugins {
                id "java" apply false
            }
        """

        when:
        fails "help"

        then:
        failure.assertHasDescription("Error resolving plugin [id: 'java', apply: false]")
        failure.assertHasCause("Plugin 'java' is a core Gradle plugin, which is already on the classpath")
        failure.assertHasFileName("Build file '$buildFile.absolutePath'")
        failure.assertHasLineNumber(3)
    }

    def "cant ask for same plugin twice"() {
        given:
        buildScript """
            plugins {
                id "java"
                id "java"
            }
        """

        when:
        fails "help"

        then:
        failure.assertThatDescription(startsWith("Plugin with id 'java' was already requested at line 3"))
        failure.assertHasFileName("Build file '$buildFile.absolutePath'")
        failure.assertHasLineNumber(4)
    }

    def "cant ask for same plugin twice with other plugins applied"() {
        given:
        buildScript """
            plugins {
                id "base"
                id "java"
                id "java"
            }
        """

        when:
        fails "help"

        then:
        failure.assertThatDescription(startsWith("Plugin with id 'java' was already requested at line 4"))
        failure.assertHasFileName("Build file '$buildFile.absolutePath'")
        failure.assertHasLineNumber(5)
    }

    def "can reapply core plugin applied via plugins block"() {
        when:
        buildScript """
            plugins {
                id "java"
            }

            assert plugins.hasPlugin("java")

            apply plugin: "java"
        """

        then:
        succeeds "help"
    }

    def "can reapply core plugin applied via qualified id in plugins block"() {
        when:
        buildScript """
            plugins {
                id "org.gradle.java"
            }

            assert plugins.hasPlugin("java")

            apply plugin: "java"
        """

        then:
        succeeds "help"
    }

    def "can use qualified and unqualified ids to detect core plugins"() {
        when:
        buildScript """
            plugins {
                id "$pluginId"
            }

            def i = 0
            plugins.withId("$QUALIFIED_JAVA") {
                ++i
            }
            plugins.withId("$UNQUALIFIED_JAVA") {
                ++i
            }
            assert i == 2

            assert plugins.getPlugin("$QUALIFIED_JAVA")
            assert plugins.getPlugin("$UNQUALIFIED_JAVA")
        """

        then:
        succeeds "help"

        where:
        pluginId << [QUALIFIED_JAVA, UNQUALIFIED_JAVA]
    }

    def "can use apply method to load core plugins qualified or unqualified"() {
        when:
        buildScript """
            apply plugin: "${pluginId}"
        """

        then:
        succeeds "clean"

        where:
        pluginId << [QUALIFIED_JAVA, UNQUALIFIED_JAVA]
    }

    def "can use apply method with other form of core plugin without problem"() {
        when:
        buildScript """
            plugins {
                id "${plugins[0]}"
            }

            apply plugin: "${plugins[1]}"
        """

        then:
        succeeds "clean"

        where:
        plugins << [QUALIFIED_JAVA, UNQUALIFIED_JAVA].permutations()
    }

}
