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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule
/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 */
class CompositeBuildDependencyArtifactsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    MavenModule publishedModuleB
    List arguments = []

    def setup() {
        publishedModuleB = mavenRepo.module("org.test", "buildB", "1.0").publish()
        new ResolveTestFixture(buildA.buildFile).prepare()

        buildA.buildFile << """
            task resolve(type: Copy) {
                from configurations.compile
                into 'libs'
            }
"""

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB
    }

    def "builds single artifact for substituted dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        resolveArtifacts()

        then:
        executed ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar')
    }

    def "builds multiple artifacts for substituted dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        and:
        buildB.buildFile << """
            task myJar(type: Jar) {
                classifier 'my'
            }

            artifacts {
                compile myJar
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:myJar", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('build/libs/buildB-1.0-my.jar')
    }

    def "builds artifacts for dependencies on multiple subprojects in the same build"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:b2:1.0'

        when:
        resolveArtifacts()

        then:
        executed ":buildB:b1:jar", ":buildB:b2:jar"
        assertResolved buildB.file('b1/build/libs/b1-1.0.jar'), buildB.file('b2/build/libs/b2-1.0.jar')
    }

    def "builds substituted dependency with transitive external dependencies"() {
        given:
        dependency 'org.test:buildB:1.0'

        def moduleC = mavenRepo.module("org.test", "buildC", "1.0").publish()
        buildB.buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'org.test:buildC:1.0'
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), moduleC.artifactFile
    }

    def "builds substituted dependency with transitive external dependency that is substituted"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            dependencies {
                compile 'org.test:buildC:1.0'
            }
"""
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildC:jar", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildC.file('build/libs/buildC-1.0.jar')
    }

    def "builds dependency once when included directly and as a transitive dependency"() {
        given:
        dependency 'org.test:buildB:1.0'
        dependency 'org.test:buildC:1.0'

        buildB.buildFile << """
            dependencies {
                compile 'org.test:buildC:1.0'
            }
"""
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildC:jar", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildC.file('build/libs/buildC-1.0.jar')
    }

    def "builds substituted dependency with transitive external dependency that is substituted in the same build"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            dependencies {
                compile 'org.test:b1:1.0'
            }
"""

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:b1:jar", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('b1/build/libs/b1-1.0.jar')
    }

    def "builds substituted dependency with transitive project dependencies"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            dependencies {
                compile project(':b1')
                compile project(path: ':b2', configuration: 'other')
            }

            project('b2') {
                configurations {
                    other
                }
                task myJar(type: Jar) {
                    classifier 'my'
                }
                artifacts {
                    other myJar
                }
            }
"""

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:b1:jar", ":buildB:jar"
        executedInOrder ":buildB:b2:myJar", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('b1/build/libs/b1-1.0.jar'), buildB.file('b2/build/libs/b2-1.0-my.jar')
    }

    def "builds substituted dependency with file dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            task myJar(type: Jar) {
                classifier 'my'
            }
            dependencies {
                compile files(myJar.archivePath) { builtBy 'myJar' }
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:myJar", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar') // File dependencies are never part of the published metadata
    }

    def "builds substituted dependency with non-default configuration"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile group: 'org.test', name: 'buildB', version: '1.0', configuration: 'other'
            }
"""

        buildB.buildFile << """
            configurations {
                other
            }
            dependencies {
                other 'org.test:buildC:1.0'
            }
            task myJar(type: Jar) {
                classifier 'my'
            }
            artifacts {
                other myJar
            }
"""
        def moduleC = mavenRepo.module("org.test", "buildC", "1.0").publish()

        when:
        resolveArtifacts()

        then:
        executed ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0-my.jar'), moduleC.artifactFile
    }

    def "builds substituted dependency with defined artifacts"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile group: 'org.test', name: 'buildB', version: '1.0', classifier: 'my'
                compile(group: 'org.test', name: 'buildB', version: '1.0') {
                    artifact {
                        name = 'another'
                        type = 'jar'
                    }
                }
            }
"""

        buildB.buildFile << """
            task myJar(type: Jar) {
                classifier 'my'
            }
            task anotherJar(type: Jar) {
                baseName 'another'
            }
            artifacts {
                compile myJar
                compile anotherJar
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0-my.jar'), buildB.file('build/libs/another-1.0.jar')
    }

    def "builds multiple configurations for the same substituted dependency"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile group: 'org.test', name: 'buildB', version: '1.0'
                compile group: 'org.test', name: 'buildB', version: '1.0', configuration: 'other'
            }
"""

        buildB.buildFile << """
            configurations {
                other
            }
            task myJar(type: Jar) {
                classifier 'my'
            }
            artifacts {
                other myJar
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:jar", ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('build/libs/buildB-1.0-my.jar')
    }

    def "does not attempt to build dependency artifacts more than once"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:b2:1.0'

        buildB.buildFile << """
            project(':b1') {
                dependencies {
                    compile project(':b2')
                }
            }
"""
        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:b2:jar", ":buildB:b1:jar"
        result.executedTasks.count {it == ":buildB:b2:jar"} == 1
        assertResolved buildB.file('b1/build/libs/b1-1.0.jar'), buildB.file('b2/build/libs/b2-1.0.jar')
    }

    def "build dependency artifacts only once when depended on by multiple subprojects"() {
        given:
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }

        buildB.buildFile << """
            project(':b1') {
                dependencies {
                    compile 'org.test:buildC:1.0'
                }
            }
            project(':b2') {
                dependencies {
                    compile 'org.test:buildC:1.0'
                }
            }
"""
        buildB.settingsFile << """
            includeBuild '${buildC.toURI()}'
"""

        when:
        execute(buildB, "jar")

        then:
        // Need to assert order separately to cater for parallel execution
        executedInOrder ":buildC:jar", ":b1:classes", ":b1:jar"
        executedInOrder ":buildC:jar", ":b2:classes", ":b2:jar"
    }

    def "builds multiple configurations for the same project via separate dependency paths"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile group: 'org.test', name: 'buildB', version: '1.0'
                compile group: 'org.test', name: 'buildC', version: '1.0'
            }
"""

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    compile group: 'org.test', name: 'buildB', version: '1.0', configuration: 'other'
                }
"""
        }
        includedBuilds << buildC

        buildB.buildFile << """
            configurations {
                other
            }
            task myJar(type: Jar) {
                classifier 'my'
            }
            artifacts {
                other myJar
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:jar", ":buildB:myJar", ":buildC:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('build/libs/buildB-1.0-my.jar'), buildC.file("build/libs/buildC-1.0.jar")
    }

    def "reports failure to build dependent artifact"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            jar.doLast {
                throw new GradleException("jar task failed")
            }
"""
        when:
        resolveArtifactsFails()

        then:
        failure
            .assertHasDescription("Failed to build artifacts for build 'buildB'")
            .assertHasCause("Execution failed for task ':buildB:jar'")
            .assertHasCause("jar task failed")
    }

    def "reports failure to build transitive dependent artifact"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            dependencies {
                compile "org.test:buildC:1.0"
            }
"""
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                jar.doLast {
                    throw new GradleException("jar task failed")
                }
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifactsFails()

        then:
        failure
            .assertHasDescription("Failed to build artifacts for build 'buildB'")
            .assertHasCause("Failed to build artifacts for build 'buildC'")
            .assertHasCause("Execution failed for task ':buildC:jar'")
            .assertHasCause("jar task failed")
    }

    private void resolveArtifacts() {
        execute(buildA, ":resolve", arguments)
    }

    private void resolveArtifactsFails() {
        fails(buildA, ":resolve", arguments)
    }

    private void assertResolved(TestFile... files) {
        String[] names = files.collect { it.name }
        buildA.file('libs').assertHasDescendants(names)
        files.each {
            buildA.file('libs/' + it.name).assertIsCopyOf(it)
        }
    }

    private void executedInOrder(String... tasks) {
        def executedTasks = result.executedTasks
        def beforeTask
        for (String task : tasks) {
            executedOnce(task)

            if (beforeTask != null) {
                assert executedTasks.indexOf(beforeTask) < executedTasks.indexOf(task) : "task ${beforeTask} must be executed before ${task}"
            }
            beforeTask = task
        }
    }
}
