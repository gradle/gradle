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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class TypeSafeProjectAccessorsIntegrationTest extends AbstractTypeSafeProjectAccessorsIntegrationTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'typesafe-project-accessors'
        """
    }

    @UnsupportedWithConfigurationCache(because = "test makes direct access to the projects extension")
    def "generates type-safe project accessors for multi-project build"() {
        given:
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

    @UnsupportedWithConfigurationCache(because = "test makes direct access to the projects extension")
    def "fails if a project doesn't follow convention"() {
        given:
        settingsFile << """
            include '1library'
        """

        when:
        fails 'help'

        then:
        failureDescriptionContains "Cannot generate project dependency accessors because project '1library' doesn't follow the naming convention: [a-zA-Z]([A-Za-z0-9\\-_])*"
    }

    @UnsupportedWithConfigurationCache(because = "test makes direct access to the projects extension")
    def "fails if two subprojects have the same java name"() {
        given:
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

    @UnsupportedWithConfigurationCache(because = "test makes direct access to the projects extension")
    def "can configure the project extension name"() {
        given:
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

    @UnsupportedWithConfigurationCache(because = "test makes direct access to the projects extension")
    def "can refer to the root project via its name"() {
        given:
        buildFile << """
            assert project(":").is(projects.typesafeProjectAccessors.dependencyProject)
        """

        when:
        run 'help'

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'
    }

    @UnsupportedWithConfigurationCache(because = "test makes direct access to the projects extension")
    def "can use the #notation notation on type-safe accessor"() {
        given:
        settingsFile << """
            include 'other'
        """

        buildFile << """
            configurations {
                implementation
            }
            dependencies {
                implementation($notation(projects.other))
            }
        """

        when:
        run 'help'

        then:
        outputDoesNotContain('it was probably created by a plugin using internal APIs')

        where:
        notation << [ 'platform', 'testFixtures']
    }

    @UnsupportedWithConfigurationCache(because = "test makes direct access to the projects extension")
    def "buildSrc project accessors are independent from the main build accessors"() {
        given:
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

    def "warns if root project name not explicitly set"() {
        //can't use the original test directory, because its name will be used as the project name and
        //it doesn't follow the project naming convention; so will use an explicitly named sub-folder

        given:
        file("project/settings.gradle") << """
            include 'one'
        """
        file("project/build.gradle") << """
            assert project(":one").is(projects.one.dependencyProject)
        """
        FeaturePreviewsFixture.enableTypeSafeProjectAccessors(file("project/settings.gradle"))

        //run once
        when:
        inDirectory 'project'
        run 'help'
        then:
        outputContains 'Project accessors enabled, but root project name not explicitly set for \'project\'.'

        //run second time
        when:
        inDirectory 'project'
        run 'help'
        then:
        if (GradleContextualExecuter.isConfigCache()) {
            outputDoesNotContain 'Project accessors enabled, but root project name not explicitly set for \'project\'.'
        } else {
            outputContains 'Project accessors enabled, but root project name not explicitly set for \'project\'.'
        }
    }

    def "does not warn if root project name explicitly set"() {
        given:
        settingsFile << """
            include 'one'
        """

        buildFile << """
            assert project(":one").is(projects.one.dependencyProject)
        """

        //run once
        when:
        run 'help'
        then:
        outputDoesNotContain 'Project accessors enabled, but root project name not explicitly set'

        //run second time
        when:
        run 'help'
        then:
        outputDoesNotContain 'Project accessors enabled, but root project name not explicitly set'
    }
}
