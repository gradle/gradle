/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

/**
 * Tests for resolving dependency graph with substitution within a composite build.
 */
class CompositeBuildDeclaredSubstitutionsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC
    ResolveTestFixture resolve
    def buildArgs = []

    def setup() {
        mavenRepo.module("org.test", "buildB", "1.0").publish()
        mavenRepo.module("org.test", "b2", "1.0").publish()

        resolve = new ResolveTestFixture(buildA.buildFile).expectDefaultConfiguration("runtime")

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    version = "2.0"

                    repositories {
                        maven { url = "${mavenRepo.uri}" }
                    }
                }
"""
        }

        buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
    }

    def "will only make declared substitutions when defined for included build"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency "org.test:b1:1.0"
        dependency "org.test:b2:1.0"

        includeBuild buildB, """
            substitute module("org.test:buildB") using project(":")
            substitute module("org.test:b1:1.0") using project(":b1")
"""

        expect:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                compositeSubstitute()
                configuration = "runtimeElements"
            }
            edge("org.test:b1:1.0", ":buildB:b1", "org.test:b1:2.0") {
                compositeSubstitute()
                configuration = "runtimeElements"
            }
            module("org.test:b2:1.0")
        }
    }

    def "can combine included builds with declared and discovered substitutions"() {
        given:
        dependency "org.test:b1:1.0"
        dependency "org.test:XXX:1.0"

        includeBuild buildB
        includeBuild buildC, """
            substitute module("org.test:XXX") using project(":")
"""

        expect:
        resolvedGraph {
            edge("org.test:b1:1.0", ":buildB:b1", "org.test:b1:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
            edge("org.test:XXX:1.0", ":buildC", "org.test:buildC:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }
    }

    def "can inject substitutions into other builds"() {
        given:
        mavenRepo.module("org.test", "plugin", "1.0").publish()

        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:XXX:1.0"

        includeBuild buildB
        includeBuild buildC, """
            substitute module("org.test:XXX") using project(":")
"""

        expect:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                edge("org.test:XXX:1.0", ":buildC", "org.test:buildC:1.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/5871")
    def "can inject substitutions into other builds when root build does not reference included builds via a dependency and included build has non-empty script classpath"() {
        mavenRepo.module("org.test", "plugin", "1.0").publish()

        given:
        buildA.buildFile << """
            task assembleB {
                dependsOn gradle.includedBuild("buildB").task(":assemble")
            }
        """
        dependency buildB, "org.test:XXX:1.0"
        buildC.buildFile.text = """
            buildscript {
                repositories { maven { url = "${mavenRepo.uri}" } }
                dependencies { classpath "org.test:plugin:1.0" }
            }
        """ + buildC.buildFile.text

        includeBuild buildB
        includeBuild buildC, """
            substitute module("org.test:XXX") using project(":")
"""

        when:
        execute(buildA, "assembleB")

        then:
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":buildC:jar")
    }

    def "can substitute arbitrary coordinates for included build"() {
        given:
        dependency "org.test:buildX:1.0"

        when:
        includeBuild buildB, """
            substitute module("org.test:buildX") using project(":b1")
"""

        then:
        resolvedGraph {
            edge("org.test:buildX:1.0", ":buildB:b1", "org.test:b1:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }
    }

    def "resolves project substitution for build based on rootProject name"() {
        given:
        def buildB2 = rootDir.file("hierarchy", "buildB");
        buildB2.file('settings.gradle') << """
            rootProject.name = 'buildB2'
"""
        buildB2.file('build.gradle') << """
            apply plugin: 'java'
            group = 'org.test'
            version = '1.0'
"""

        dependency "org.gradle:buildX:1.0"

        when:
        // The project path ':' is resolved using the rootProject.name of buildB2
        includeBuild buildB2, """
            substitute module("org.gradle:buildX") using project(":")
"""

        then:
        resolvedGraph {
            edge("org.gradle:buildX:1.0", ":buildB", "org.test:buildB2:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }
    }

    def "substitutes external dependency with project dependency from same participant build"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:b2:1.0"

        when:
        includeBuild buildB, """
            substitute module("org.test:buildB") using project(":")
            substitute module("org.test:b2:1.0") using project(":b2")
"""

        then:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                edge("org.test:b2:1.0", ":buildB:b2", "org.test:b2:2.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                }
            }
        }
    }

    def "preserves the requested attributes when performing a composite substitution"() {
        platformDependency 'org.test:platform:1.0'

        def platform = file("platform")

        file("platform/build.gradle") << """
            plugins {
                id 'java-platform'
            }

            group = 'org.test'
            version = '2.0'
        """
        file("platform/settings.gradle") << """
            rootProject.name = 'platform'
        """

        when:
        includeBuild(platform)

        then:
        resolvedGraph {
            edge("org.test:platform:1.0", ":platform", "org.test:platform:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                noArtifacts()
            }
        }

    }

    def "preserves the requested attributes when performing a composite substitution using mapping"() {
        platformDependency 'org.test:platform:1.0'

        def platform = file("platform")

        file("platform/build.gradle") << """
            plugins {
                id 'java-platform'
            }
        """
        file("platform/settings.gradle") << """
            rootProject.name = 'platform'
        """

        when:
        includeBuild(platform, """
            substitute $source using $dest
        """)

        then:
        resolvedGraph {
            edge("org.test:platform:1.0", ":platform", ":platform:") {
                configuration = "runtimeElements"
                compositeSubstitute()
                noArtifacts()
            }
        }

        where:
        source                                  | dest
        'platform(module("org.test:platform"))' | 'platform(project(":"))'
        'module("org.test:platform")'           | 'platform(project(":"))'
        'platform(module("org.test:platform"))' | 'project(":")'
        'module("org.test:platform")'           | 'project(":")'
        'module("org.test:platform")'           | 'project(":")'
    }

    def "preserves the requested capabilities when performing a composite substitution"() {
        buildA.buildFile << """
            dependencies {
                implementation('org.test:buildB:1.0') {
                    capabilities {
                        requireCapability 'org.test:buildB-test-fixtures'
                    }
                }
            }
        """

        buildB.buildFile << """
            apply plugin: 'java-test-fixtures'
        """
        when:
        includeBuild buildB

        then:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "testFixturesRuntimeElements"
                compositeSubstitute()
                artifact name: 'buildB'
                artifact classifier: 'test-fixtures'
                project(":buildB", "org.test:buildB:2.0") {
                }
            }
        }

    }

    def "preserves the requested capabilities when performing a composite substitution using mapping"() {
        buildA.buildFile << """
            dependencies {
                implementation('org.test:buildB:1.0') {
                    capabilities {
                        requireCapability 'org.test:buildB-test-fixtures'
                    }
                }
            }
        """

        buildB.buildFile << """
            apply plugin: 'java-test-fixtures'
        """

        when:
        includeBuild buildB, """
            substitute $source using $dest
        """

        then:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "testFixturesRuntimeElements"
                compositeSubstitute()
                artifact name: 'buildB'
                artifact classifier: 'test-fixtures'
                project(":buildB", "org.test:buildB:2.0") {
                }
            }
        }

        where:
        source                                                                                                  | dest
        "module('org.test:buildB')"                                                                             | "project(':')"
        "variant(module('org.test:buildB')) { capabilities { requireCapability('org:buildB-test-fixtures') } }" | "project(':')"
        "module('org.test:buildB')"                                                                             | "variant(project(':')) { capabilities { requireCapability('org:should-not-be-used') } }"
    }

    @Issue("https://github.com/gradle/gradle/issues/15659")
    def "resolves dependencies of included build with dependency substitution when substitution build contains buildSrc"() {
        given:
        includeBuild(buildB, """
            substitute(module("org.test:b1")).using(project(":b1"))
        """)
        includeBuild(buildC)
        buildC.buildFile << """
            dependencies {
                implementation('org.test:b1:1.0')
            }
        """

        when:
        // presence of buildSrc build causes IllegalStateException for the execution below
        buildB.file("buildSrc/build.gradle").touch()

        then:
        execute(buildA, ":buildC:dependencies")
    }

    @Issue("https://github.com/gradle/gradle/issues/15659")
    def "builds included build with dependency substitution when substitution build contains buildSrc"() {
        given:
        includeBuild(buildB, """
            substitute(module("org.test:b1")).using(project(":b1"))
        """)
        includeBuild(buildC)
        buildC.buildFile << """
            dependencies {
                implementation('org.test:b1:1.0')
            }
        """

        when:
        // presence of buildSrc build causes IllegalStateException for the execution below
        buildB.file("buildSrc/build.gradle").touch()

        then:
        execute(buildA, ":buildC:build")
    }

    @Issue("https://github.com/gradle/gradle/issues/15659")
    def "resolves dependencies of included build with dependency substitution when substitution build uses a plugin from its build-logic build"() {
        given:
        includeBuild(buildB, """
            substitute(module("org.test:b1")).using(project(":b1"))
        """)
        includeBuild(buildC)
        buildC.buildFile << """
            dependencies {
                implementation('org.test:b1:1.0')
            }
        """

        when:
        buildB.file("build-logic/build.gradle") << """
            plugins {
                id("groovy-gradle-plugin")
            }
        """
        buildB.file("build-logic/src/main/groovy/foo.gradle") << """
            println("foo applied")
        """
        buildB.settingsFile.setText("""
            pluginManagement {
                includeBuild('build-logic')
            }
            ${buildB.settingsFile.text}
        """)
        buildB.buildFile.setText("""
            plugins {
                id("java-library")
                id("foo")
            }
        """)

        then:
        execute(buildA, ":buildC:dependencies")
    }

    void resolvedGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.prepare()
        execute(buildA, ":checkDeps", buildArgs)
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
