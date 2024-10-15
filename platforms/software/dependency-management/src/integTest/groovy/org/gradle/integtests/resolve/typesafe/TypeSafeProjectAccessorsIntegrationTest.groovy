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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class TypeSafeProjectAccessorsIntegrationTest extends AbstractTypeSafeProjectAccessorsIntegrationTest {
    def resolve = new ResolveTestFixture(buildFile)

    def setup() {
        settingsFile << """
            rootProject.name = 'typesafe-project-accessors'
        """
    }

    def "generates type-safe project accessors for multi-project build"() {
        given:
        includeProjects("one", "one:other", "two", "two:other")

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(projects.one)
                implementation(projects.one.other)
                implementation(projects.two.other)
            }
        """
        resolve.prepare("runtimeClasspath")

        when:
        succeeds(":checkDeps")

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'
        resolve.expectGraph {
            root(":", ":typesafe-project-accessors:unspecified") {
                project(":one", "typesafe-project-accessors:one:1.0")
                project(":one:other", "typesafe-project-accessors.one:other:1.0")
                project(":two:other", "typesafe-project-accessors.two:other:1.0")
            }
        }
    }

    def "fails if a project doesn't follow convention"() {
        given:
        createDirs("1library")
        settingsFile << """
            include '1library'
        """

        when:
        fails 'help'

        then:
        failureDescriptionContains "Cannot generate project dependency accessors because project '1library' doesn't follow the naming convention: [a-zA-Z]([A-Za-z0-9\\-_])*"
    }

    def "fails if two subprojects have the same java name"() {
        given:
        createDirs("super-cool", "super--cool")
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
        given:
        includeProjects("one", "one:other", "two", "two:other")
        settingsFile << """
            dependencyResolutionManagement {
                defaultProjectsExtensionName.set("ts")
            }
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(ts.one)
                implementation(ts.one.other)
                implementation(ts.two.other)
            }
        """
        resolve.prepare("runtimeClasspath")

        when:
        succeeds(":checkDeps")

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'
        resolve.expectGraph {
            root(":", ":typesafe-project-accessors:unspecified") {
                project(":one", "typesafe-project-accessors:one:1.0")
                project(":one:other", "typesafe-project-accessors.one:other:1.0")
                project(":two:other", "typesafe-project-accessors.two:other:1.0")
            }
        }
    }

    def "can refer to the root project via its name"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            sourceSets {
                foo
            }

            java {
                registerFeature('foo') {
                    usingSourceSet(sourceSets.foo)
                }
            }

            dependencies {
                implementation(projects.typesafeProjectAccessors) {
                    capabilities {
                        it.requireFeature("foo")
                    }
                }
            }
        """
        resolve.prepare("runtimeClasspath")

        when:
        succeeds(':checkDeps')

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'
        resolve.expectGraph {
            root(":", ":typesafe-project-accessors:unspecified") {
                project(":", ":typesafe-project-accessors:unspecified") {
                    artifact(name: 'typesafe-project-accessors-foo', fileName: 'typesafe-project-accessors-foo.jar')
                }
            }
        }
    }

    def "can use the #notation notation on type-safe accessor"() {
        given:
        settingsFile << """
            include 'other'
        """
        file("other/build.gradle") << """
            plugins {
                id("java-platform")
                id("java-test-fixtures")
            }
        """

        buildFile << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation($notation(projects.other))
            }
        """
        resolve.prepare("runtimeClasspath")

        when:
        succeeds(':checkDeps')

        then:
        outputDoesNotContain('it was probably created by a plugin using internal APIs')
        resolve.expectGraph {
            root(":", ":typesafe-project-accessors:unspecified") {
                project(":other", "typesafe-project-accessors:other:unspecified") {
                    if (notation == "platform") {
                        noArtifacts()
                    } else {
                        artifact(name: 'other-test-fixtures', fileName: 'other-test-fixtures.jar')
                    }
                }
            }
        }

        where:
        notation << [ 'platform', 'testFixtures']
    }

    def "buildSrc project accessors are independent from the main build accessors"() {
        given:
        file("buildSrc/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(projects.one)
                implementation(projects.two)
            }
        """
        ["one", "two"].each {
            file("buildSrc/settings.gradle") << """
                include '$it'
            """
            file("buildSrc/$it/build.gradle") << """
                plugins {
                    id("java-library")
                }
                version = "1.0"
            """
        }
        FeaturePreviewsFixture.enableTypeSafeProjectAccessors(file("buildSrc/settings.gradle"))
        def resolveBuildSrc = new ResolveTestFixture(file("buildSrc/build.gradle"))
        resolveBuildSrc.prepare("runtimeClasspath")

        includeProjects("three", "four")
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(projects.three)
                implementation(projects.four)
            }
        """
        resolve.prepare("runtimeClasspath")

        when:
        succeeds(":buildSrc:checkDeps")
        succeeds(":checkDeps")

        then:
        outputContains 'Type-safe project accessors is an incubating feature.'

        // We cannot verify resolveBuildSrc since it has a ton of extra things on the
        // classpath by default

        resolve.expectGraph {
            root(":", ":typesafe-project-accessors:unspecified") {
                project(":three", "typesafe-project-accessors:three:1.0")
                project(":four", "typesafe-project-accessors:four:1.0")
            }
        }
    }

    def "warns if root project name not explicitly set"() {
        //can't use the original test directory, because its name will be used as the project name and
        //it doesn't follow the project naming convention; so will use an explicitly named sub-folder

        given:
        createDirs("project", "project/one")
        file("project/settings.gradle") << """
            include 'one'
        """
        file("project/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(projects.one)
            }
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
        includeProjects("one")

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(projects.one)
            }
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

    def includeProjects(String... names) {
        names.each {
            includeProject(it)
        }
    }

    def includeProject(String name) {
        settingsFile << """
            include '$name'
        """
        file("${name.replace(':', '/')}/build.gradle") << """
            plugins {
                id("java-library")
            }
            version = "1.0"
        """
    }

}
