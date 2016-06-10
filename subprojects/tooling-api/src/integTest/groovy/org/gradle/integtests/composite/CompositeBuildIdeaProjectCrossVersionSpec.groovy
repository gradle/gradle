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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.plugins.ide.fixtures.IdeaFixtures
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository

/**
 * Tests for generating IDEA metadata for projects within a composite build.
 */
@TargetGradleVersion(">=3.0")
class CompositeBuildIdeaProjectCrossVersionSpec extends AbstractCompositeBuildIntegrationTest {
    TestFile buildA
    TestFile buildB
    MavenFileRepository mavenRepo

    def setup() {
        mavenRepo = new MavenFileRepository(file("maven-repo"))

        buildA = singleProjectBuild("buildA") {
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
"""
        }

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    apply plugin: 'idea'
                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                }
"""
        }
        builds = [buildA, buildB]
    }

    def "builds IDEA metadata with substituted dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "buildB"

        and:
        executed ":ideaModule", ":buildB:ideaModule", ":buildB:b1:ideaModule", ":buildB:b2:ideaModule"
        !result.executedTasks.contains(":buildA:ideaModule")
        imlHasNoDependencies(buildB)
        imlHasNoDependencies(buildB.file("b1"))
        imlHasNoDependencies(buildB.file("b2"))
    }

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

    def "builds IDEA metadata with substituted dependency from same build"() {
        given:
        dependency(buildB, "org.test:b1:1.0")

        when:
        idea(buildB)

        then:
        iprHasModules(buildB, "buildB.iml", "b1/b1.iml", "b2/b2.iml", "../buildA/buildA.iml")
        imlHasDependencies(buildB, "b1")
    }

    def "builds IDEA metadata with substituted subproject dependency that has transitive dependencies"() {
        given:
        def transitive1 = mavenRepo.module("org.test", "transitive1").publish()
        mavenRepo.module("org.test", "transitive2").dependsOn(transitive1).publish()

        dependency "org.test:buildB:1.0"
        dependency(buildB, "org.test:transitive2:1.0")

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies(["buildB"], ["transitive1-1.0.jar", "transitive2-1.0.jar"])
    }

    def "builds IDEA metadata with substituted subproject dependency that has transitive project dependency"() {
        given:
        dependency "org.test:buildB:1.0"
        buildB.buildFile << """
            dependencies {
                compile project(':b1')
            }
"""

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "buildB", "b1"
    }

    def "builds IDEA metadata with transitive substitutions"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
"""
        }
        builds << buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml", "../buildC/buildC.iml"
        imlHasDependencies "buildB", "buildC"
    }

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

    def "builds IDEA metadata with dependency cycle between substituted projects in a multiproject build"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            dependencies {
                compile "org.test:b1:1.0"
            }
            project(':b1') {
                apply plugin: 'java'
                dependencies {
                    compile "org.test:b2:1.0"
                }
            }
            project(':b2') {
                apply plugin: 'java'
                dependencies {
                    compile "org.test:b1:1.0"
                }
            }
"""

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        imlHasDependencies "buildB", "b1", "b2"
    }

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

    def "builds IDEA metadata in composite containing participants with same root directory name"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency "org.test:buildC:1.0"

        def buildC = rootDir.file("hierarchy", "buildB");
        buildC.file('settings.gradle') << """
            rootProject.name = 'buildC'
"""
        buildC.file('build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'idea'

            group = 'org.test'
            version = '1.0'
"""
        builds << buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml", "../hierarchy/buildB/buildC.iml"
        imlHasDependencies "buildB", "buildC"
    }

    def "generated IDEA project references modules for all projects in composite"() {
        given:

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
"""
        }
        builds << buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml", "../buildC/buildC.iml"

        and:
        executed ":ideaModule", ":buildB:ideaModule", ":buildB:b1:ideaModule", ":buildB:b2:ideaModule", ":buildC:ideaModule"
        imlHasNoDependencies(buildA)
        imlHasNoDependencies(buildB)
        imlHasNoDependencies(buildB.file("b1"))
        imlHasNoDependencies(buildB.file("b2"))
        imlHasNoDependencies(buildC)
    }

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

    def "builds IDEA when one participant does not have IDEA plugin applied"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency "org.test:buildC:1.0"

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        builds << buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml"
        // TODO:DAZ This is invalid: no `buildC.iml` file exists in the project. Should not substitute?
        imlHasDependencies "buildB", "buildC"
    }

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
                        maven { url "${mavenRepo.uri}" }
                    }
                }
                project(":c2") {
                    apply plugin: 'idea'
                }
"""
        }

        builds << buildC

        when:
        idea()

        then:
        iprHasModules "buildA.iml", "../buildB/buildB.iml", "../buildB/b1/b1.iml", "../buildB/b2/b2.iml", "../buildC/c2/c2.iml"
        // TODO:DAZ This is invalid: no `buildC.iml` file exists in the project. Should not substitute?
        imlHasDependencies "b1", "buildC", "c2"
    }

    def dependency(TestFile buildRoot = buildA, String notation) {
        buildRoot.buildFile << """
            dependencies {
                compile '${notation}'
            }
"""
    }

    def idea(TestFile projectDir = buildA) {
        execute(projectDir, ":idea")
    }

    def ipr(TestFile projectDir = buildA) {
        def iprFile = projectDir.file(projectDir.name + ".ipr")
        assert iprFile.exists()
//        println iprFile.text
        return IdeaFixtures.parseIpr(iprFile)
    }

    def iml(TestFile projectDir = buildA) {
        def imlFile = projectDir.file(projectDir.name + ".iml")
        assert imlFile.exists()
//        println imlFile.text
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
