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

import org.gradle.plugins.ide.idea.IdeaFixtures
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository

/**
 * Tests for generating IDEA metadata for projects within a composite build.
 */
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
        def imlA = iml(buildA)
        imlA.dependencies.modules.size() == 1
        imlA.dependencies.assertHasModule('buildB')
        imlA.dependencies.libraries.empty
    }

    def "builds IDEA metadata with substituted subproject dependencies"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:b2:1.0'

        when:
        idea()

        then:
        def imlA = iml(buildA)
        imlA.dependencies.modules.size() == 2
        imlA.dependencies.assertHasModule('b1')
        imlA.dependencies.assertHasModule('b2')
        imlA.dependencies.libraries.empty
    }

    def "builds IDEA metadata with substituted dependency from same build"() {
        given:
        dependency(buildB, "org.test:b1:1.0")

        when:
        idea(buildB)

        then:
        def imlB = iml(buildB)
        imlB.dependencies.modules.size() == 1
        imlB.dependencies.assertHasModule('b1')
        imlB.dependencies.libraries.empty
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
        def imlA = iml(buildA).dependencies
        imlA.modules.size() == 1
        imlA.assertHasModule('buildB')
        imlA.libraries.size() == 2
        imlA.assertHasLibrary('transitive1-1.0.jar')
        imlA.assertHasLibrary('transitive2-1.0.jar')
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
        def imlA = iml(buildA).dependencies
        imlA.modules.size() == 2
        imlA.assertHasModule('buildB')
        imlA.assertHasModule('b1')
        imlA.libraries.empty
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
        def imlA = iml(buildA).dependencies
        imlA.modules.size() == 2
        imlA.assertHasModule('buildB')
        imlA.assertHasModule('buildC')
        imlA.libraries.empty
    }

    def "builds IDEA metadata with substituted transitive dependency"() {
        given:
        mavenRepo.module("org.external", "external-dep", '1.0').dependsOn("org.test", "buildB", "1.0").publish()
        dependency "org.external:external-dep:1.0"

        when:
        idea()

        then:
        def imlA = iml(buildA).dependencies
        imlA.libraries.size() == 1
        imlA.assertHasLibrary("external-dep-1.0.jar")
        imlA.modules.size() == 1
        imlA.assertHasModule('buildB')
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
        def imlA = iml(buildA).dependencies
        imlA.libraries.empty
        imlA.modules.size() == 3
        imlA.assertHasModule('buildB')
        imlA.assertHasModule('b1')
        imlA.assertHasModule('b2')
    }

    def "builds IDEA metadata with dependency cycle between substituted participants in a composite build"() {
        given:
        dependency(buildA, "org.test:buildB:1.0")
        dependency(buildB, "org.test:buildA:1.0")

        when:
        idea()

        then:
        def imlA = iml(buildA).dependencies
        imlA.libraries.empty
        imlA.modules.size() == 1
        imlA.assertHasModule('buildB')
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
        def imlA = iml(buildA).dependencies
        imlA.libraries.empty
        imlA.modules.size() == 2
        imlA.assertHasModule('buildB')
        imlA.assertHasModule('buildC')
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

    def getIpr() {
        return IdeaFixtures.parseIpr(file("root.ipr"))
    }

    def iml(TestFile projectDir = buildA) {
        def imlFile = projectDir.file(projectDir.name + ".iml")
        assert imlFile.exists()
        println imlFile.text
        return IdeaFixtures.parseIml(imlFile)
    }

}
