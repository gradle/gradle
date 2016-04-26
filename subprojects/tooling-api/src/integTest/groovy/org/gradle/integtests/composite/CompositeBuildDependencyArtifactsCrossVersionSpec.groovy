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
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.tooling.BuildException
/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 * Note that this test should be migrated to use the command-line entry point for composite build, when this is developed.
 * This is distinct from the specific test coverage for Tooling API access to a composite build.
 */
@TargetGradleVersion(ToolingApiVersions.SUPPORTS_INTEGRATED_COMPOSITE)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_INTEGRATED_COMPOSITE)
class CompositeBuildDependencyArtifactsCrossVersionSpec extends CompositeToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()
    def stdErr = new ByteArrayOutputStream()
    TestFile buildA
    TestFile buildB
    MavenModule publishedModuleB
    List builds
    MavenFileRepository mavenRepo

    def setup() {
        onlyIntegratedComposite()

        mavenRepo = new MavenFileRepository(file("maven-repo"))
        publishedModuleB = mavenRepo.module("org.test", "buildB", "1.0").publish()

        buildA = singleProjectBuild("buildA") {
            buildFile << """
                apply plugin: 'java'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                task resolve(type: Copy) {
                    from configurations.compile
                    into 'libs'
                }
"""
        }

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        builds = [buildA, buildB]
    }

    def "resolves configuration with single artifact"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        resolveArtifacts()

        then:
        assertResolved buildB.file('build/libs/buildB-1.0.jar')
        result.executedTasks.contains ":buildB:jar"
    }

    def "resolves configuration in subproject with single artifact"() {
        given:
        dependency 'org.test:b1:1.0'

        when:
        resolveArtifacts()

        then:
        assertResolved buildB.file('b1/build/libs/b1-1.0.jar')
        result.executedTasks.contains ":buildB:b1:jar"
    }

    def "resolves configuration with multiple artifacts"() {
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
        result.executedTasks.containsAll([":buildB:myJar", ":buildB:jar"])
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('build/libs/buildB-1.0-my.jar')
    }

    def "resolves configuration with transitive external dependencies"() {
        given:
        dependency 'org.test:buildB:1.0'

        def moduleC = mavenRepo.module("org.test", "buildC", "1.0").publish()
        buildB.buildFile << """
            dependencies {
                compile 'org.test:buildC:1.0'
            }
"""

        when:
        resolveArtifacts()

        then:
        result.executedTasks.containsAll([":buildB:jar"])
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), moduleC.artifactFile
    }

    def "resolves configuration with transitive external dependency that is substituted"() {
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
        builds << buildC

        when:
        resolveArtifacts()

        then:
        result.executedTasks.containsAll([":buildB:jar", ":buildC:jar"])
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildC.file('build/libs/buildC-1.0.jar')
    }

    def "resolves configuration with transitive project dependencies"() {
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
        result.executedTasks.containsAll([":buildB:jar", ":buildB:b1:jar", ":buildB:b2:myJar"])
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('b1/build/libs/b1-1.0.jar'), buildB.file('b2/build/libs/b2-1.0-my.jar')
    }

    def "resolves substituted dependency with non-default configuration"() {
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
        result.executedTasks.contains ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0-my.jar'), moduleC.artifactFile
    }

    def "resolves substituted dependency with defined artifacts"() {
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
        result.executedTasks.contains ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0-my.jar'), buildB.file('build/libs/another-1.0.jar')
    }

    def "resolves multiple configurations for the same substituted dependency"() {
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
        result.executedTasks.containsAll([":buildB:jar", ":buildB:myJar"])
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('build/libs/buildB-1.0-my.jar')
    }

    @NotYetImplemented
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
        assertResolved buildB.file('b1/build/libs/b1-1.0.jar'), buildB.file('b2/build/libs/b2-1.0.jar')
        result.executedTasks.containsAll([":buildB:b1:jar", ":buildB:b2:jar"])
        result.executedTasks.count {it == ":buildB:b2:jar"} == 1
    }

    @NotYetImplemented
    def "reports failure to resolve artifacts with dependency cycle between substituted participants in a composite build"() {
        given:
        dependency "org.test:${fromBuildA}:1.0"
        buildB.buildFile << """
            dependencies {
                compile "org.test:${fromBuildB}:1.0"
            }
            project(":b1") {
                dependencies {
                    compile "org.test:buildB:1.0"
                }
            }
"""

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
            apply plugin: 'java'
            dependencies {
                compile "org.test:${fromBuildC}:1.0"
            }
"""
        }
        builds << buildC

        when:
        resolveArtifacts()

        then:
        def t = thrown(BuildException)
        assertFailure(t, "Cyclic dependency error")

        where:
        fromBuildA | fromBuildB | fromBuildC
        "buildB"   | "buildA"   | "."
        "buildB"   | "buildC"   | "buildA"
        "buildB"   | "buildC"   | "buildB"
        "buildB"   | "b1"       | "."       // b1 -> buildB
    }

    @NotYetImplemented
    def "reports failure to build dependent artifact"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            jar.doLast {
                throw new GradleException("jar task failed")
            }
"""
        when:
        resolveArtifacts()

        then:
        def t = thrown(BuildException)
        assertFailure(t, "jar task failed")
        assertFailure(t, "Execution failed for task ':buildB:jar'")
    }

    @NotYetImplemented
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
        builds << buildC

        when:
        resolveArtifacts()

        then:
        def t = thrown(BuildException)
        assertFailure(t, "jar task failed")
        assertFailure(t, "Execution failed for task ':buildC:jar'")
    }

    def dependency(String notation) {
        buildA.buildFile << """
            dependencies {
                compile '${notation}'
            }
"""
    }

    private void resolveArtifacts() {
        execute(buildA, ":resolve")
    }

    private void assertResolved(TestFile... files) {
        String[] names = files.collect { it.name }
        buildA.file('libs').assertHasDescendants(names)
        files.each {
            buildA.file('libs/' + it.name).assertIsCopyOf(it)
        }
    }

    private void execute(File build, String... tasks) {
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.setStandardError(stdErr)
            buildLauncher.forTasks(build, tasks)
            buildLauncher.run()
        }
    }

    private ExecutionResult getResult() {
        return new OutputScrapingExecutionResult(stdOut.toString(), stdErr.toString())
    }

    void checkGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
