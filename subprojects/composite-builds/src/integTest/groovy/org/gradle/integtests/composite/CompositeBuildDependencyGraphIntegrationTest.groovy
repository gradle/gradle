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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.integtests.fixtures.SuggestionsMessages.repositoryHint

/**
 * Tests for resolving dependency graph with substitution within a composite build.
 */
class CompositeBuildDependencyGraphIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    ResolveTestFixture resolve
    def buildArgs = []

    def setup() {
        mavenRepo.module("org.test", "buildB", "1.0").publish()

        resolve = new ResolveTestFixture(buildA.buildFile).expectDefaultConfiguration("runtime")

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java-library'
                    version "2.0"

                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                }
            """
        }
        includedBuilds << buildB
    }

    def "reports failure to configure one participant build"() {
        given:
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                throw new RuntimeException('exception thrown on configure')
            """
        }
        includedBuilds << buildC

        when:
        checkDependenciesFails()

        then:
        failure.assertHasDescription("A problem occurred evaluating project ':buildC'.")
            .assertHasCause("exception thrown on configure")
    }

    def "does no substitution when no project matches external dependencies"() {
        given:
        mavenRepo.module("org.different", "buildB", "1.0").publish()
        mavenRepo.module("org.test", "buildC", "1.0").publish()

        buildA.buildFile << """
            dependencies {
                implementation "org.different:buildB:1.0"
                implementation "org.test:buildC:1.0"
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
                implementation "org.test:buildB:1.0"
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }

        and:
        executed ":buildB:jar"
    }

    def "can resolve dependency graph without building artifacts"() {
        given:
        resolve.withoutBuildingArtifacts()

        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }

        and:
        notExecuted ":buildB:jar"
    }

    def "substitutes external dependencies with project dependencies using --include-build"() {
        given:
        singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        withArgs(["--include-build", '../buildB', "--include-build", '../buildC'])
        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
                implementation "org.test:buildC:1.0"
            }
        """
        includedBuilds = []

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
            edge("org.test:buildC:1.0", ":buildC", "org.test:buildC:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }
    }

    def "substitutes external dependencies with subproject dependencies"() {
        given:
        buildA.buildFile << """
            dependencies {
                implementation "org.test:b1:1.0"
                implementation "org.test:b2:1.0"
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:b1:1.0", ":buildB:b1", "org.test:b1:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
            edge("org.test:b2:1.0", ":buildB:b2", "org.test:b2:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }
    }

    def "substitutes external dependency with project dependency from same participant build"() {
        given:
        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
            }
        """
        buildB.buildFile << """
            dependencies {
                implementation "org.test:b2:1.0"
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
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

    def "substitutes external dependency with subproject dependency that has transitive dependencies"() {
        given:
        def transitive1 = mavenRepo.module("org.test", "transitive1").publish()
        mavenRepo.module("org.test", "transitive2").dependsOn(transitive1).publish()
        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
            }
        """
        buildB.buildFile << """
            dependencies {
                implementation "org.test:transitive2:1.0"
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                module("org.test:transitive2:1.0") {
                    module("org.test:transitive1:1.0")
                }
            }
        }
    }

    def "substitutes external dependency with subproject dependency that has transitive project dependencies"() {
        given:
        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
            }
        """
        createDirs("buildB", "buildB/b1", "buildB/b1/b11")
        buildB.settingsFile << """
            include ':b1:b11'
        """
        buildB.buildFile << """
            dependencies {
                implementation project(':b1')
            }

            project(":b1") {
                dependencies {
                    implementation project("b11") // Relative project path
                }
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                project(":buildB:b1", "org.test:b1:2.0") {
                    project(":buildB:b1:b11", "org.test:b11:2.0")
                }
            }
        }
    }

    def "honours excludes defined in substituted subproject dependency that has transitive dependencies"() {
        given:
        def transitive1 = mavenRepo.module("org.test", "transitive1").publish()
        mavenRepo.module("org.test", "transitive2").dependsOn(transitive1).publish()
        buildA.buildFile << """
            dependencies {
                implementation("org.test:buildB:1.0")
            }
        """
        buildB.buildFile << """
            dependencies {
                implementation("org.test:transitive2:1.0")  {
                    exclude module: 'transitive1'
                }
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                module("org.test:transitive2:1.0")
            }
        }
    }

    def "substitutes transitive dependency of substituted project dependency"() {
        given:
        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
            }
        """
        buildB.buildFile << """
            dependencies {
                implementation "org.test:buildC:1.0"
            }
        """
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        includedBuilds << buildC

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                edge("org.test:buildC:1.0", ":buildC", "org.test:buildC:1.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                }
            }
        }
    }

    def "substitutes transitive dependency of non-substituted external dependency"() {
        given:
        mavenRepo.module("org.external", "external-dep", '1.0').dependsOn("org.test", "buildB", "1.0").publish()

        buildA.buildFile << """
            dependencies {
                implementation "org.external:external-dep:1.0"
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            module("org.external:external-dep:1.0") {
                edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                    compositeSubstitute()
                }
            }
        }
    }

    def "substitutes transitive dependency with forced version"() {
        given:
        mavenRepo.module("org.external", "external-dep", '1.0').dependsOn("org.test", "buildB", "1.0").publish()

        buildA.buildFile << """
            dependencies {
                implementation "org.external:external-dep:1.0"
            }
            configurations.runtimeClasspath.resolutionStrategy.force("org.test:buildB:5.0")
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            module("org.external:external-dep:1.0") {
                edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                    forced()
                    compositeSubstitute()
                }
            }
        }
    }

    def "substitutes transitive dependency based on result of resolution rules"() {
        given:
        mavenRepo.module("org.external", "external-dep", '1.0')
            .dependsOn("org.test", "something", "1.0")
            .dependsOn("org.other", "something-else", "1.0")
            .publish()

        buildA.buildFile << """
            dependencies {
                implementation "org.external:external-dep:1.0"
            }
            configurations.runtimeClasspath.resolutionStrategy {
                eachDependency { DependencyResolveDetails details ->
                    if (details.requested.name == 'something') {
                        details.useTarget "org.test:buildB:1.0"
                    }
                }
                dependencySubstitution {
                    substitute module("org.other:something-else:1.0") using module("org.test:b1:1.0")
                }
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            module("org.external:external-dep:1.0") {
                edge("org.test:something:1.0", ":buildB", "org.test:buildB:2.0") {
                    selectedByRule()
                    compositeSubstitute()
                }
                edge("org.other:something-else:1.0", ":buildB:b1", "org.test:b1:2.0") {
                    selectedByRule()
                    compositeSubstitute()
                }
            }
        }
    }

    def "evaluates subprojects when substituting external dependencies with #name"() {
        given:
        buildA.buildFile << """
            dependencies {
                implementation "group.requires.subproject.evaluation:b1:1.0"
            }
        """

        buildB.file("b1", "build.gradle") << """
            afterEvaluate {
                group = 'group.requires.subproject.evaluation'
            }
        """

        when:
        withArgs(args)
        checkDependencies()

        then:
        checkGraph {
            edge("group.requires.subproject.evaluation:b1:1.0", ":buildB:b1", "group.requires.subproject.evaluation:b1:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }

        where:
        name                  | args
        "regular build"       | []
        "configure on demand" | ["--configure-on-demand"]
        "parallel"            | ["--parallel"]
    }

    def "substitutes dependency in composite containing participants with same root directory name"() {
        given:
        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
                implementation "org.test:buildC:1.0"
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

        includeBuildAs(buildC, 'buildC')

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
            edge("org.test:buildC:1.0", ":buildC", "org.test:buildC:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
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
        includedBuilds << buildC

        buildA.buildFile << """
            dependencies {
                implementation "org.test:b1:1.0"
                implementation "org.test:c1:1.0"
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:b1:1.0", ":buildB:b1", "org.test:b1:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
            edge("org.test:c1:1.0", ":buildC:c1", "org.test:c1:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
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
        includedBuilds << buildC

        buildA.buildFile << """
            dependencies {
                implementation "org.test:b1:1.0"
            }
        """

        when:
        checkDependenciesFails()

        then:
        failure.assertHasCause("Module version 'org.test:b1:1.0' is not unique in composite: can be provided by [project :buildB:b1, project :buildC:b1].")
    }

    def "reports failure to resolve dependencies when substitution is ambiguous within single participant"() {
        given:
        buildB
        def buildC = multiProjectBuild("buildC", ['c1', 'c2']);
        createDirs("buildC", "buildC/nested", "buildC/nested/c1")
        buildC.settingsFile << """
            include ':nested:c1'
        """
        buildC.buildFile << """
            allprojects {
                apply plugin: 'java'
            }
        """
        includedBuilds << buildC

        buildA.buildFile << """
            dependencies {
                implementation "org.test:c1:1.0"
            }
        """

        when:
        checkDependenciesFails()

        then:
        failure.assertHasCause("Module version 'org.test:c1:1.0' is not unique in composite: can be provided by [project :buildC:c1, project :buildC:nested:c1].")
    }

    def "reports failure to resolve dependencies when transitive dependency substitution is ambiguous"() {
        given:
        transitiveDependencyIsAmbiguous("'org.test:b1:2.0'")

        when:
        checkDependenciesFails()

        then:
        failure.assertHasCause("Module version 'org.test:b1:2.0' is not unique in composite: can be provided by [project :buildB:b1, project :buildC:b1].")
    }

    def "resolve transitive project dependency that is ambiguous in the composite"() {
        given:
        transitiveDependencyIsAmbiguous("project(':b1')")

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                project(":buildB:b1", "org.test:b1:2.0")
            }
        }
    }

    def transitiveDependencyIsAmbiguous(String dependencyNotation) {
        def buildC = multiProjectBuild("buildC", ['b1']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    version = '3.0'
                }
            """
        }
        includedBuilds << buildC

        buildB.buildFile << """
            dependencies {
                implementation ${dependencyNotation}
            }
        """

        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
            }
        """
    }

    def "handles unused participant with no defined configurations"() {
        given:
        def buildC = singleProjectBuild("buildC")
        includedBuilds << buildC

        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildB:1.0"
            }
        """

        when:
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
            }
        }
    }

    def "reports failure when substituted project does not have requested configuration"() {
        given:
        def buildC = singleProjectBuild("buildC")
        includedBuilds << buildC

        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildC:1.0"
            }
        """

        when:
        checkDependenciesFails()

        then: "Build C does not have any configurations defined, and thus no variants exist"
        failure.assertHasCause("""No matching variant of project :buildC was found. The consumer was configured to find a library for use during runtime, compatible with Java ${JavaVersion.current().majorVersion}, packaged as a jar, preferably optimized for standard JVMs, and its dependencies declared externally but:
  - No variants exist.""")
    }

    public static final REPOSITORY_HINT = repositoryHint("Maven POM")
    @ToBeFixedForConfigurationCache(because = "different error reporting")
    def "includes build identifier in error message on failure to resolve dependencies of included build"() {
        def m = mavenRepo.module("org.test", "test", "1.2")

        given:
        def buildC = singleProjectBuild("buildC")
        includedBuilds << buildC

        buildA.buildFile << """
            dependencies {
                implementation "org.test:buildC:1.0"
            }
        """
        buildC.buildFile << """
            repositories {
                maven { url '$mavenRepo.uri' }
            }

            configurations {
                buildInputs
                create('default')
            }

            dependencies {
                buildInputs "org.test:test:1.2"
            }

            task buildOutputs {
                inputs.files configurations.buildInputs
                outputs.upToDateWhen { false }
                doLast {
                    configurations.buildInputs.each { }
                }
            }

            artifacts {
                "default" file: file("out.jar"), builtBy: buildOutputs
            }
        """

        when:
        checkDependenciesFails()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':buildC:buildOutputs'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':buildC:buildInputs'.")
        failure.assertHasCause("""Could not find org.test:test:1.2.
Searched in the following locations:
  - ${m.pom.file.displayUri}
Required by:
    project :buildC""")
        failure.assertHasResolutions(REPOSITORY_HINT,
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)


        when:
        m.publish()
        m.artifact.file.delete()

        checkDependenciesFails()

        then:
        failure.assertHasDescription("Execution failed for task ':buildC:buildOutputs'.")
        failure.assertHasCause("Could not resolve all files for configuration ':buildC:buildInputs'.")
        failure.assertHasCause("Could not find test-1.2.jar (org.test:test:1.2).")
    }

    def "substitutes external dependency for a subproject of the root build - #rootIsIncluded"() {
        given:
        def empty = file('empty').tap {
            it.mkdir()
        }
        mavenRepo.module("org.test", "subproject1", "2.0").publish()
        buildA.buildFile << """
            subprojects {
                apply plugin: 'java-library'
                group = 'org.test'
                version = '1.0'
            }
            dependencies {
                implementation "org.test:subproject1:2.0"
            }
        """
        includedBuilds = [empty]
        createDirs("buildA", "buildA/subproject1")
        buildA.settingsFile << """
            include('subproject1')
            $includeRootStatement
        """

        when:
        checkDependencies()

        then:
        def expectSubstitution = rootIsIncluded.contains("yes")
        checkGraph {
            if (expectSubstitution) {
                edge("org.test:subproject1:2.0", ":subproject1", "org.test:subproject1:1.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                }
            } else {
                module("org.test:subproject1:2.0")
            }
        }

        and:
        if (expectSubstitution) {
            executed(":subproject1:jar")
        } else {
            notExecuted(":subproject1:jar")
        }

        where:
        rootIsIncluded          | includeRootStatement
        'no'                    | ""
        'yes'                   | "includeBuild('.')"
        // If substitutions for the root would be controllable (e.g. by the first include statement encountered) this would enable you to not have the ':subproject1' substitution
        // This documents the current behavior. It is unclear if this is a useful functionality to have.
        'yes with substitution' | "includeBuild('.') { dependencySubstitution { substitute module('org.test:subproject2') with project(':subproject2') } }"
    }

    private void withArgs(List<String> args) {
        buildArgs = args as List
    }

    private void checkDependencies() {
        resolve.prepare()
        execute(buildA, ":checkDeps", buildArgs)
    }

    private void checkDependenciesFails() {
        resolve.prepare()
        fails(buildA, ":checkDeps", buildArgs)
    }

    void checkGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
