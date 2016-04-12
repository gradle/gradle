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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.BuildException

/**
 * Tests for dependency substitution within a composite build.
 * Note that this test should be migrated to use the command-line entry point for composite build, when this is developed.
 * This is distinct from the specific test coverage for Tooling API access to a composite build.
 */
@TargetGradleVersion(ToolingApiVersions.SUPPORTS_INTEGRATED_COMPOSITE)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_INTEGRATED_COMPOSITE)
class CompositeBuildDependencySubstitutionCrossVersionSpec extends CompositeToolingApiSpecification {
    def buildA
    def buildB
    List builds
    def mavenRepo
    ResolveTestFixture resolve

    def setup() {
        mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.test", "buildB", "1.0").publish()

        buildA = multiProjectBuild("buildA", ['a1', 'a2']) {
            buildFile << """
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                allprojects {
                    apply plugin: 'base'
                    configurations { compile }
                }
"""
        }
        resolve = new ResolveTestFixture(buildA.buildFile)
        resolve.prepare()

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    version "2.0"
                }
"""
        }
        builds = [buildA, buildB]
    }

    def "reports failure to configure one participant build"() {
        given:
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                throw new RuntimeException('exception thrown on configure')
"""
        }
        builds << buildC

        when:
        checkDependencies()

        then:
        def t = thrown(BuildException)
        assertFailure(t,
            "A problem occurred evaluating root project 'buildC'.",
            "exception thrown on configure")
    }

    def "does no substitution when no project matches external dependencies"() {
        given:
        mavenRepo.module("org.different", "buildB", "1.0").publish()
        mavenRepo.module("org.test", "buildC", "1.0").publish()

        buildA.buildFile << """
            dependencies {
                compile "org.different:buildB:1.0"
                compile "org.test:buildC:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            module("org.different:buildB:1.0")
            module("org.test:buildC:1.0")
        }
    }

    def "substitutes external dependency with root project dependency"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:buildB:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project buildB::", "org.test:buildB:2.0") {
                selectedByRule()
            }
        }
    }

    def "substitutes external dependencies with subproject dependencies"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:b1:1.0"
                compile "org.test:b2:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:b1:1.0", "project buildB::b1", "org.test:b1:2.0") {
                selectedByRule()
            }
            edge("org.test:b2:1.0", "project buildB::b2", "org.test:b2:2.0") {
                selectedByRule()
            }
        }
    }

    def "substitutes external dependency with project dependency from same build"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:a2:1.0"
            }
            project(':a2') {
                apply plugin: 'java' // Ensure that the project produces a jar
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:a2:1.0", "project buildA::a2", "org.test:a2:1.0") {
                selectedByRule()
            }
        }
    }

    def "substitutes external dependency with subproject dependency that has transitive dependencies"() {
        given:
        mavenRepo.module("org.foo", "transitive", "1.0").publish()
        buildA.buildFile << """
            dependencies {
                compile "org.test:buildB:1.0"
            }
"""
        buildB.buildFile << """
            dependencies {
                it.'default' "org.foo:transitive:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project buildB::", "org.test:buildB:2.0") {
                selectedByRule()
                module("org.foo:transitive:1.0")
            }
        }
    }

    def "substitutes transitive dependency of substituted project dependency"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:buildB:1.0"
            }
"""
        buildB.buildFile << """
            dependencies {
                it.'default' "org.test:buildC:1.0"
            }
"""
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        builds << buildC

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project buildB::", "org.test:buildB:2.0") {
                selectedByRule()
                edge("org.test:buildC:1.0", "project buildC::", "org.test:buildC:1.0") {
                    selectedByRule()
                }
            }
        }
    }

    def "substitutes transitive dependency of non-substituted external dependency"() {
        given:
        mavenRepo.module("org.external", "external-dep", '1.0').dependsOn("org.test", "buildB", "1.0").publish()

        buildA.buildFile << """
            dependencies {
                compile "org.external:external-dep:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            module("org.external:external-dep:1.0") {
                edge("org.test:buildB:1.0", "project buildB::", "org.test:buildB:2.0") {
                    selectedByRule()
                }
            }
        }
    }

    def "can resolve with dependency cycle between substituted projects in a multiproject build"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:a1:1.0"
            }
            project(':a1') {
                apply plugin: 'java'
                dependencies {
                    compile "org.test:a2:1.0"
                }
            }
            project(':a2') {
                apply plugin: 'java'
                dependencies {
                    compile "org.test:a1:1.0"
                }
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:a1:1.0", "project buildA::a1", "org.test:a1:1.0") {
                selectedByRule()
                edge("org.test:a2:1.0", "project buildA::a2", "org.test:a2:1.0") {
                    selectedByRule()
                    edge("org.test:a1:1.0", "project buildA::a1", "org.test:a1:1.0") {}
                }
            }
        }
    }

    def "can resolve with dependency cycle between substituted participants in a composite build"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:buildB:1.0"
            }
"""
        buildB.buildFile << """
            dependencies {
                compile "org.test:buildA:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project buildB::", "org.test:buildB:2.0") {
                selectedByRule()
                edge("org.test:buildA:1.0", "project :", "org.test:buildA:1.0") {}
            }
        }
    }

    @NotYetImplemented
    def "substitutes dependency in composite containing participants with same root directory name"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:buildB:1.0"
                compile "org.test:buildC:1.0"
            }
"""

        def buildC = rootDir.file("hierarchy", "buildB");
        buildC.file('settings.gradle') << """
            rootProject.name = 'buildC'
"""
        buildC.file('build.gradle') << """
            apply plugin: 'java'
            group = 'org.test'
            version = '1.0'
"""
        builds << buildC

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project buildB::", "org.test:buildB:2.0") {
                selectedByRule()
            }
            edge("org.test:buildC:1.0", "project buildC::", "org.test:buildC:1.0") {
                selectedByRule()
            }
        }
    }

    def "can substitute dependencies in composite with duplicate publication if not involved in resolution"() {
        given:
        def buildC = multiProjectBuild("buildC", ['a2', 'b2', 'c1']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        builds << buildC

        buildA.buildFile << """
            dependencies {
                compile "org.test:b1:1.0"
                compile "org.test:c1:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:b1:1.0", "project buildB::b1", "org.test:b1:2.0") {
                selectedByRule()
            }
            edge("org.test:c1:1.0", "project buildC::c1", "org.test:c1:1.0") {
                selectedByRule()
            }
        }
    }

    def "reports failure to resolve dependencies when substitution is ambiguous"() {
        given:
        def buildC = multiProjectBuild("buildC", ['a1', 'b1']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    version = '3.0'
                }
"""
        }
        builds << buildC

        buildA.buildFile << """
            dependencies {
                compile "org.test:b1:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        def t = thrown(BuildException)
        assertFailure(t, "Module version 'org.test:b1:1.0' is not unique in composite: can be provided by projects [buildB::b1, buildC::b1].")
    }

    def "reports failure to resolve dependencies when substitution is ambiguous within single participant"() {
        given:
        buildB
        def buildC = multiProjectBuild("buildC", ['c1', 'c2']);
        buildC.settingsFile << """
            include ':nested:c1'
"""
        buildC.buildFile << """
            allprojects {
                apply plugin: 'java'
            }
"""
        builds << buildC

        buildA.buildFile << """
            dependencies {
                compile "org.test:c1:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        def t = thrown(BuildException)
        assertFailure(t, "Module version 'org.test:c1:1.0' is not unique in composite: can be provided by projects [buildC::c1, buildC::nested:c1].")
    }

    def "handles unused participant with no defined configurations"() {
        given:
        def buildC = singleProjectBuild("buildC")
        builds << buildC

        buildA.buildFile << """
            dependencies {
                compile "org.test:buildB:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project buildB::", "org.test:buildB:2.0") {
                selectedByRule()
            }
        }
    }

    def "reports failure when substituted project does not have requested configuration"() {
        given:
        def buildC = singleProjectBuild("buildC")
        builds << buildC

        buildA.buildFile << """
            dependencies {
                compile "org.test:buildC:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        def t = thrown(BuildException)
        assertFailure(t, "Module version org.test:buildA:1.0, configuration 'compile' declares a dependency on configuration 'default' which is not declared in the module descriptor for org.test:buildC:1.0")
    }

    private void checkDependencies() {
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.setStandardOutput(System.out)
            buildLauncher.forTasks(buildA, ":checkDeps")
            buildLauncher.run()
        }
    }

    void checkGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
