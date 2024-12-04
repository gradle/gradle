/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.plugins.ide.fixtures.IdeaFixtures
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

/**
 * Tests for generating IDEA metadata for projects within a composite build.
 */
class CompositeBuildIdeaProjectIntegrationTest extends AbstractIntegrationSpec {
    BuildTestFile buildA
    BuildTestFile buildB

    def setup() {
        buildTestFixture.withBuildInSubDir()
        buildA = singleProjectBuild("buildA") {
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
"""
        }

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java-library'
                    apply plugin: 'idea'
                    repositories {
                        maven { url = "${mavenRepo.uri}" }
                    }
                }
"""
        }
        includeBuild(buildB)
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with substituted dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "buildB"
        imlHasNoDependencies(buildB)
        imlHasNoDependencies(buildB.file("b1"))
        imlHasNoDependencies(buildB.file("b2"))

        and:
        executed ":ideaModule", ":buildB:ideaModule", ":buildB:b1:ideaModule", ":buildB:b2:ideaModule"
        notExecuted ":buildA:ideaModule"
        notExecuted ":buildB:jar", ":buildB:b1:jar", ":buildB:b2:jar"
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with substituted subproject dependencies"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:b2:1.0'

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "b1", "b2"
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with substituted dependency from same build"() {
        given:
        dependency('org.test:buildB:1.0')
        dependency(buildB, "org.test:b1:1.0")

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies(buildB, "b1")
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with substituted subproject dependency that has transitive dependencies"() {
        given:
        def transitive1 = mavenRepo.module("org.test", "transitive1").publish()
        mavenRepo.module("org.test", "transitive2").dependsOn(transitive1).publish()

        dependency "org.test:buildB:1.0"
        apiDependency(buildB, "org.test:transitive2:1.0")

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies(["buildB"], ["transitive1-1.0.jar", "transitive2-1.0.jar", ])
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with substituted subproject dependency that has transitive project dependency"() {
        given:
        dependency "org.test:buildB:1.0"
        buildB.buildFile << """
            dependencies {
                api project(':b1')
            }
"""

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "buildB", "b1"
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with transitive substitutions"() {
        given:
        dependency "org.test:buildB:1.0"
        apiDependency buildB, "org.test:buildC:1.0"

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
"""
        }
        includeBuild buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml", "../buildC/buildC.iml"
        imlHasDependencies "buildB", "buildC"
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with substituted transitive dependency"() {
        given:
        mavenRepo.module("org.external", "external-dep", '1.0').dependsOn("org.test", "buildB", "1.0").publish()
        dependency "org.external:external-dep:1.0"

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies(["buildB"], ["external-dep-1.0.jar"])
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with dependency cycle between substituted projects in a multiproject build"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            dependencies {
                api "org.test:b1:1.0"
            }
            project(':b1') {
                apply plugin: 'java'
                dependencies {
                    api "org.test:b2:1.0"
                }
            }
            project(':b2') {
                apply plugin: 'java'
                dependencies {
                    api "org.test:b1:1.0"
                }
            }
"""

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "buildB", "b1", "b2"
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata with dependency cycle between substituted participants in a composite build"() {
        given:
        dependency(buildA, "org.test:buildB:1.0")
        dependency(buildB, "org.test:buildA:1.0")

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "buildB"
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata in composite containing participants with same root directory name"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency "org.test:buildC:1.0"

        def buildC = file("hierarchy", "buildB")
        buildC.file('settings.gradle') << """
            rootProject.name = 'buildC'
"""
        buildC.file('build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'idea'

            group = 'org.test'
            version = '1.0'
"""
        includeBuild buildC, "buildC"

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml", "../hierarchy/buildB/buildC.iml"
        imlHasDependencies "buildB", "buildC"
    }

    @ToBeFixedForConfigurationCache
    def "generated IDEA project references modules for all projects in composite"() {
        given:

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
"""
        }
        includeBuild buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml", "../buildC/buildC.iml"
        imlHasNoDependencies(buildA)
        imlHasNoDependencies(buildB)
        imlHasNoDependencies(buildB.file("b1"))
        imlHasNoDependencies(buildB.file("b2"))
        imlHasNoDependencies(buildC)

        and:
        executed ":ideaModule", ":buildB:ideaModule", ":buildB:b1:ideaModule", ":buildB:b2:ideaModule", ":buildC:ideaModule"
        notExecuted ":buildB:jar", ":buildB:b1:jar", ":buildB:b2:jar"
        notExecuted ":buildC:jar"
    }

    @ToBeFixedForConfigurationCache
    def "generated IDEA metadata respects idea plugin configuration"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:b2:1.0'

        buildB.buildFile << """
            project(':b1') {
                idea.module.name = 'b1-renamed'
            }
"""

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1-renamed.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "b1-renamed", "b2"
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA when one participant does not have IDEA plugin applied"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency "org.test:buildC:1.0"

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includeBuild buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        // This is actually invalid: no `buildC.iml` file exists in the project. Should generated buildC iml file even when plugin not applied.
        imlHasDependencies "buildB", "buildC"
    }

    @ToBeFixedForConfigurationCache
    def "builds IDEA metadata when not all projects have IDEA plugin applied"() {
        given:
        dependency "org.test:b1:1.0"
        dependency "org.test:buildC:1.0"
        dependency "org.test:c2:1.0"

        def buildC = multiProjectBuild("buildC", ['c1', 'c2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    repositories {
                        maven { url = "${mavenRepo.uri}" }
                    }
                }
                project(":c2") {
                    apply plugin: 'idea'
                }
"""
        }

        includeBuild buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml", "../buildC/c2/c2.iml"
        // This is actually invalid: no `buildC.iml` file exists in the project. Should generated buildC iml file even when plugin not applied.
        imlHasDependencies "b1", "buildC", "c2"
    }

    @ToBeFixedForConfigurationCache
    def "de-duplicates module names for included builds"() {
        given:
        dependency "org.test:b1:1.0"
        dependency "org.buildC:b1:1.0"
        dependency "org.buildD:b1:1.0"

        def buildC = multiProjectBuild("buildC", ['b1']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    apply plugin: 'idea'
                    group = 'org.buildC'
                }
"""
        }
        includeBuild buildC

        def buildD = singleProjectBuild("b1") {
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
                group = 'org.buildD'
"""
        }
        includeBuild buildD

        when:
        idea()

        then:
        iprHasModules "buildA.iml",
            "../buildB/buildB.iml",
            "../buildB/b1/buildB-b1.iml",
            "../buildB/b2/b2.iml",
            "../buildC/buildC.iml",
            "../buildC/b1/buildC-b1.iml",
            "../b1/buildA-b1.iml"

        imlHasDependencies "buildB-b1", "buildC-b1", "buildA-b1"
    }

    @ToBeFixedForConfigurationCache
    def "de-duplicates module names between including and included builds"() {
        given:
        buildA.buildFile << """
            dependencies {
                implementation 'org.buildC:buildA:1.0'
                implementation project(':b1')
                implementation 'org.test:b1:1.0'
            }
"""

        buildA.addChildDir("b1")
        buildA.settingsFile << """
            include 'b1'
"""
        buildA.buildFile << """
            subprojects {
                apply plugin: 'idea'
                apply plugin: 'java'

                group = 'org.buildA'
            }
"""

        def buildC = multiProjectBuild("buildC", ["buildA"]) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    apply plugin: 'idea'

                    group = 'org.buildC'
                }
"""
        }
        includeBuild buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml",
            "b1/buildA-b1.iml",
            "../buildB/buildB.iml",
            "../buildB/b1/buildB-b1.iml",
            "../buildB/b2/b2.iml",
            "../buildC/buildC.iml",
            "../buildC/buildA/buildC-buildA.iml"

        imlHasDependencies "buildC-buildA", "buildA-b1", "buildB-b1"
    }

    @ToBeImplemented
    @Issue("https://github.com/gradle/gradle/issues/2526")
    @ToBeFixedForConfigurationCache
    def "de-duplicates module names when not all projects have IDEA plugin applied"() {
        given:
        dependency "org.test:b1:1.0"
        dependency "org.buildC:b1:1.0"
        dependency "org.buildD:b1:1.0"

        def buildC = multiProjectBuild("buildC", ['b1']) {
            buildFile << """
                // Idea plugin only applied to root
                apply plugin: 'idea'

                allprojects {
                    apply plugin: 'java'
                    group = 'org.buildC'
                }
"""
        }
        includeBuild buildC

        def buildD = singleProjectBuild("b1") {
            buildFile << """
                apply plugin: 'java'
                group = 'org.buildD'
"""
        }
        includeBuild buildD

        when:
        idea()

        then:
        // TODO Each of these should have modules
        iprHasModules([
            "buildA.iml",
            "../buildB/buildB.iml",
            "../buildB/b1/buildB-b1.iml",
            "../buildB/b2/b2.iml",
            "../buildC/buildC.iml",
            // "../buildC/b1/buildC-b1.iml",
            // "../b1/b1.iml"
        ] as String[])

        // TODO Each of these should have dependencies
        imlHasDependencies(
            "buildB-b1",
            // "buildC-b1",
            "b1"
        )
    }

    def dependency(BuildTestFile sourceBuild = buildA, String notation) {
        sourceBuild.buildFile << """
            dependencies {
                implementation '${notation}'
            }
"""
    }

    def apiDependency(BuildTestFile sourceBuild, String notation) {
        sourceBuild.buildFile << """
            dependencies {
                api '${notation}'
            }
"""
    }

    def includeBuild(TestFile build, String name = null) {
        if (name) {
            buildA.settingsFile << """
                includeBuild '${build.toURI()}', {
                    name = '$name'
                }
            """
        } else {
            buildA.settingsFile << """
                includeBuild '${build.toURI()}'
            """
        }
    }

    def idea(TestFile build = buildA) {
        executer.inDirectory(build)
        succeeds(":idea")
    }

    def ipr(TestFile projectDir = buildA) {
        def iprFile = projectDir.file(projectDir.name + ".ipr")
        assert iprFile.exists()
        return IdeaFixtures.parseIpr(iprFile)
    }

    def iml(TestFile projectDir = buildA) {
        def imlFile = projectDir.file(projectDir.name + ".iml")
        assert imlFile.exists()
        return IdeaFixtures.parseIml(imlFile)
    }

    private void iprHasModules(TestFile projectDir = buildA, String... expected) {
        def modules = ipr(projectDir).modules
        assert modules.modules.size() == expected.length
        expected.each {
            modules.assertHasModule(it)
        }
    }

    private void imlHasDependencies(TestFile projectDir = buildA, String... expectedModules) {
        imlHasDependencies(projectDir, expectedModules as List<String>, [])
    }

    private void imlHasNoDependencies(TestFile projectDir = buildA) {
        imlHasDependencies(projectDir, [], [])
    }

    private void imlHasDependencies(TestFile projectDir = buildA, List<String> expectedModules, List<String> expectedLibraries) {
        def dependencies = iml(projectDir).dependencies
        assert dependencies.modules.size() == expectedModules.size()
        expectedModules.each {
            dependencies.assertHasModule(it)
        }
        assert dependencies.libraries.size() == expectedLibraries.size()
        expectedLibraries.each {
            dependencies.assertHasLibrary(it)
        }
    }
}
