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

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.test.fixtures.maven.MavenFileRepository

import static org.gradle.integtests.tooling.fixture.TextUtil.normaliseLineSeparators
/**
 * Tests for dependency substitution within a composite build.
 * Note that this test should be migrated to use the command-line entry point for composite build, when this is developed.
 * This is distinct from the specific test coverage for Tooling API access to a composite build.
 */
@TargetGradleVersion(ToolingApiVersions.SUPPORTS_INTEGRATED_COMPOSITE)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_INTEGRATED_COMPOSITE)
class CompositeBuildDependencySubstitutionCrossVersionSpec extends CompositeToolingApiSpecification {
    // TODO:DAZ Use ResolveTestFixture in here, instead of parsing 'dependencies' output
    def stdOut = new ByteArrayOutputStream()
    def buildA
    def buildB
    List builds
    def mavenRepo

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
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'base'
                }
"""
        }
        builds = [buildA, buildB]
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
        output.contains """
compile
+--- org.different:buildB:1.0
\\--- org.test:buildC:1.0
"""
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
        output.contains """
compile
\\--- org.test:buildB:1.0 -> project buildB::
"""
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
        output.contains """
compile
+--- org.test:b1:1.0 -> project buildB::b1
\\--- org.test:b2:1.0 -> project buildB::b2
"""
    }

    def "substitutes external dependency with project dependency from same build"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:a2:1.0"
            }
"""

        when:
        checkDependencies()

        then:
        output.contains """
compile
\\--- org.test:a2:1.0 -> project buildA::a2
"""
        // TODO:DAZ This should render like a local project dependency (not qualified with 'buildA:')
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
        output.contains """
compile
\\--- org.test:buildB:1.0 -> project buildB::
     \\--- org.foo:transitive:1.0
"""
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
                apply plugin: 'base'
"""
        }
        builds << buildC

        when:
        checkDependencies()

        then:
        output.contains """
compile
\\--- org.test:buildB:1.0 -> project buildB::
     \\--- org.test:buildC:1.0 -> project buildC::
"""
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
        output.contains """
compile
\\--- org.external:external-dep:1.0
     \\--- org.test:buildB:1.0 -> project buildB::
"""
    }

    private void checkDependencies() {
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.forTasks(buildA, "dependencies")
            buildLauncher.run()
        }
    }

    def getOutput() {
        normaliseLineSeparators(stdOut.toString())
    }
}
