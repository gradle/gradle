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

package org.gradle.integtests.tooling.r213
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.RequiresIntegratedComposite
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.maven.MavenFileRepository

import static org.gradle.integtests.tooling.fixture.TextUtil.normaliseLineSeparators
/**
 * Dependency substitution is performed for composite build accessed via the `GradleConnection` API.
 */
@TargetGradleVersion(">=1.4")
// Dependencies task fails for missing dependencies with older Gradle versions
class DependencySubstitutionGradleConnectionCrossVersionSpec extends CompositeToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()
    def stdErr = new ByteArrayOutputStream()
    def buildA
    def buildB
    def builds = []
    def mavenRepo

    def setup() {
        mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.test", "buildB", "1.0").publish()

        buildA = singleProjectBuild("buildA") {
            buildFile << """
        configurations { compile }
        dependencies {
            compile "org.test:buildB:1.0"
        }
        repositories {
            maven { url '${mavenRepo.uri}' }
        }
"""
        }
        buildB = singleProjectBuild("buildB") {
            buildFile << """
        apply plugin: 'java'
"""
        }
        builds << buildA << buildB
    }

    def "dependencies report shows external dependencies substituted with project dependencies"() {
        given:
        def expectedOutput = "org.test:buildB:1.0"
        if (isIntegratedComposite()) {
            expectedOutput = "org.test:buildB:1.0 -> project buildB::"
        }

        when:
        dependencies()

        then:
        output.contains """
compile
\\--- $expectedOutput
"""
    }

    @RequiresIntegratedComposite
    def "dependencies report displays failure for dependency that cannot be resolved in composite"() {
        given:
        // Add a project that makes 'buildB' ambiguous in the composite
        def buildC = multiProjectBuild('buildC', ['buildB'])
        builds << buildC

        when:
        dependencies()

        then:
        output.contains """
compile
\\--- org.test:buildB:1.0 FAILED
"""
    }

    @RequiresIntegratedComposite
    def "builds artifacts for substituted dependencies"() {
        given:
        buildA.buildFile << """
            task printConfiguration(dependsOn: configurations.compile) << {
                configurations.compile.each { println it }
            }
"""
        buildB.buildFile << """
            dependencies {
                compile "org.test:buildC:1.0"
            }
"""
        def buildC = singleProjectBuild('buildC') {
            buildFile << """
                apply plugin: 'java'
"""
        }
        builds << buildC

        when:
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.setStandardError(stdErr)
            buildLauncher.forTasks(buildA, "printConfiguration")
            buildLauncher.run()
        }

        then:
        result.assertTasksExecuted(
            ":printConfiguration",
            ":buildB:compileJava",
            ":buildC:compileJava",
            ":buildC:processResources",
            ":buildC:classes",
            ":buildC:jar",
            ":buildB:processResources",
            ":buildB:classes",
            ":buildB:jar")
    }

    private Object dependencies() {
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.forTasks(buildA, "dependencies")
            buildLauncher.run()
        }
    }

    def getResult() {
        return new OutputScrapingExecutionResult(stdOut.toString(), stdErr.toString())
    }

    def getOutput() {
        normaliseLineSeparators(stdOut.toString())
    }

    def getError() {
        normaliseLineSeparators(stdErr.toString())
    }
}
