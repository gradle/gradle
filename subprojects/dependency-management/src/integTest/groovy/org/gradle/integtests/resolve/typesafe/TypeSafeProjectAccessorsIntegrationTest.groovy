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

package org.gradle.integtests.resolve.typesafe

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "tests make direct access to the projects extension")
class TypeSafeProjectAccessorsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'typesafe-project-accessors'
        """
        FeaturePreviewsFixture.enableTypeSafeProjectAccessors(settingsFile)
    }

    def "generates type-safe project accessors for multi-project build"() {
        settingsFile << """
            include 'one'
            include 'one:other'
            include 'two:other'
        """

        buildFile << """
            assert project(":one").is(projects.one.dependencyProject)
            assert project(":one:other").is(projects.one.other.dependencyProject)
            assert project(":two:other").is(projects.two.other.dependencyProject)
        """

        when:
        run 'help'

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'
    }

    def "warns if a project doesn't follow kebab-case convention"() {
        settingsFile << """
            include 'oneLibrary'
        """

        buildFile << """
            tasks.register("noExtension") {
                doLast {
                    assert extensions.findByName('projects') == null
                }
            }
        """

        when:
        succeeds 'noExtension'

        then:
        outputContains "Cannot generate project dependency accessors because project 'oneLibrary' doesn't follow the kebab case naming convention: [a-z]([a-z0-9\\-])*"
    }

    def "warns if two subprojects have the same java name"() {
        settingsFile << """
            include 'super-cool'
            include 'super--cool'
        """

        buildFile << """
            tasks.register("noExtension") {
                doLast {
                    assert extensions.findByName('projects') == null
                }
            }
        """

        when:
        succeeds 'noExtension'

        then:
        outputContains "Cannot generate project dependency accessors because subprojects [super-cool, super--cool] of project : map to the same method name getSuperCool()"
    }

    def "can configure the project extension name"() {
        settingsFile << """
            include 'one'
            include 'one:other'
            include 'two:other'

            dependencyResolutionManagement {
                defaultProjectsExtensionName.set("ts")
            }
        """

        buildFile << """
            assert project(":one").is(ts.one.dependencyProject)
            assert project(":one:other").is(ts.one.other.dependencyProject)
            assert project(":two:other").is(ts.two.other.dependencyProject)
        """

        when:
        run 'help'

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'
    }

}
