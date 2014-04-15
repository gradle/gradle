/*
 * Copyright 2013 the original author or authors.
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

class PluginUseDslIntegrationSpec extends AbstractIntegrationSpec {

    def "can use plugins block in project build scripts"() {
        when:
        buildScript """
          plugins {
            id "foo"
            id "bar" version "1.0"
          }
        """

        then:
        succeeds "help"
    }

    void "plugins block has no implicit access to owner context"() {
        when:
        buildScript """
            plugins {
                try {
                    buildscript {}
                } catch(MissingMethodException e) {
                    // ok
                }

                try {
                    version
                } catch(MissingPropertyException e) {
                    // ok
                }

                assert delegate == null
                assert this instanceof ${PluginDependenciesSpec.name}
                assert owner == this

                println "end-of-plugins"
            }
        """

        then:
        succeeds "tasks"
        and:
        output.contains("end-of-plugins")
    }

    def "buildscript blocks are allowed before plugin statements"() {
        when:
        buildScript """
            buildscript {}
            plugins {}
        """

        then:
        succeeds "tasks"
    }

    def "buildscript blocks are not allowed after plugin blocks"() {
        when:
        buildScript """
            plugins {}
            buildscript {}
        """

        then:
        fails "tasks"

        and:
        failure.assertHasLineNumber 3
        errorOutput.contains("all buildscript {} blocks must appear before any plugins {} blocks")
    }

    def "build logic cannot precede plugins block"() {
        when:
        buildScript """
            someThing()
            plugins {}
        """

        then:
        fails "tasks"

        and:
        failure.assertHasLineNumber 3
        errorOutput.contains "only buildscript {} and and other plugins {} script blocks are allowed before plugins {} blocks, no other statements are allowed"
    }

    def "build logic cannot precede any plugins block"() {
        when:
        buildScript """
            plugins {}
            someThing()
            plugins {}
        """

        then:
        fails "tasks"

        and:
        failure.assertHasLineNumber 4
        errorOutput.contains "only buildscript {} and and other plugins {} script blocks are allowed before plugins {} blocks, no other statements are allowed"
    }


}
