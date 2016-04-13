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
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.junit.Assume

import static org.gradle.integtests.tooling.fixture.TextUtil.normaliseLineSeparators

/**
 * Dependency substitution is performed for composite build accessed via the `GradleConnection` API.
 */
@TargetGradleVersion(">=1.4") // Dependencies task fails for missing dependencies with older Gradle versions
class DependencySubstitutionGradleConnectionCrossVersionSpec extends CompositeToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()
    def buildA
    def buildB
    def builds = []

    def setup() {
        buildA = singleProjectBuild("buildA") {
                    buildFile << """
        configurations { compile }
        dependencies {
            compile "org.test:buildB:1.0"
        }
"""
}
        buildB = singleProjectBuild("buildB") {
                    buildFile << """
        apply plugin: 'base'
"""
}
        builds << buildA << buildB
    }

    def "dependencies report shows external dependencies substituted with project dependencies"() {
        given:
        def expectedOutput = "org.test:buildB:1.0 FAILED"
        if (supportsIntegratedComposites()) {
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

    def "dependencies report displays failure for dependency that cannot be resolved in composite"() {
        Assume.assumeTrue(supportsIntegratedComposites())

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

    private Object dependencies() {
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
