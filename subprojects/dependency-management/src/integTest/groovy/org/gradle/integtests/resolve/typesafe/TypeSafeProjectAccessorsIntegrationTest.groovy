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

import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "tests make direct access to the projects extension")
class TypeSafeProjectAccessorsIntegrationTest extends AbstractTypeSafeProjectAccessorsIntegrationTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'typesafe-project-accessors'
        """
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

    def "fails if a project doesn't follow convention"() {
        settingsFile << """
            include '1library'
        """

        when:
        fails 'help'

        then:
        failureDescriptionContains "Cannot generate project dependency accessors because project '1library' doesn't follow the naming convention: [a-zA-Z]([A-Za-z0-9\\-_])*"
    }

    def "fails if two subprojects have the same java name"() {
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
        fails 'noExtension'

        then:
        failureDescriptionContains "Cannot generate project dependency accessors because subprojects [super-cool, super--cool] of project : map to the same method name getSuperCool()"
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

    def "can refer to the root project via its name"() {
        buildFile << """
            assert project(":").is(projects.typesafeProjectAccessors.dependencyProject)
        """

        when:
        run 'help'

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'
    }

    def "buildSrc project accessors are independent from the main build accessors"() {
        file("buildSrc/build.gradle") << """
            assert project(":one").is(projects.one.dependencyProject)
            assert project(":two").is(projects.two.dependencyProject)
        """
        file("buildSrc/settings.gradle") << """
            include 'one'
            include 'two'
        """
        FeaturePreviewsFixture.enableTypeSafeProjectAccessors(file("buildSrc/settings.gradle"))
        settingsFile << """
            include 'one'
            include 'two'
        """

        buildFile << """
            assert project(":one").is(projects.one.dependencyProject)
            assert project(":two").is(projects.two.dependencyProject)
        """

        when:
        run 'help'

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'
    }
}
