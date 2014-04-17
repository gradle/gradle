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

package org.gradle.plugin

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.Matchers.startsWith

class CorePluginUseIntegrationSpec extends AbstractIntegrationSpec {

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
        fails "tasks"

        then:
        failure.assertThatDescription(startsWith("Plugin 'java' is a core Gradle plugin, which cannot be specified with a version number"))
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
        fails "tasks"

        then:
        failure.assertThatDescription(startsWith("Plugin 'org.gradle.java' is a core Gradle plugin, which cannot be specified with a version number"))
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
        fails "tasks"

        then:
        failure.assertThatDescription(startsWith("Plugin with id 'java' was already requested at line 3"))
        failure.assertHasFileName("Build file '$buildFile.absolutePath'")
        failure.assertHasLineNumber(4)
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
        succeeds "tasks"
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
        succeeds "tasks"
    }

}
