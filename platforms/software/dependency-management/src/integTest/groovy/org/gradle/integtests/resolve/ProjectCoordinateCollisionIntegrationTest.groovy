/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.api.problems.Severity
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.problems.ReceivedProblem
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/847")
class ProjectCoordinateCollisionIntegrationTest extends AbstractIntegrationSpec {

    private final ResolveTestFixture resolve = new ResolveTestFixture(testDirectory)

    private static final String COLLISION_PROBLEM_FQID = "dependency-resolution:project-coordinate-collision"

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a:foo'
            include 'b:foo'
        """
    }

    def "the collision is reported as a Problem, and the dependency resolves to the wrong project"() {
        given:
        enableProblemsApiCheck()
        collidingProjects()

        when:
        succeeds ":a:foo:checkDeps"

        then: "the dependency on ':b:foo' selects ':a:foo', the project doing the resolving"
        resolve.expectGraphOnly(":a:foo") {
            root(":a:foo", "org.test:foo:1.0") {
                edge("project ':b:foo'", ":a:foo", "org.test:foo:1.0")
            }
        }

        and:
        verifyAll(receivedProblem) {
            fqid == COLLISION_PROBLEM_FQID
            definition.severity == Severity.WARNING
            contextualLabel == collisionWarning(":a:foo:runtimeClasspath", "org.test:foo", ":a:foo", ":b:foo")
            details.endsWith("Run with :a:foo:dependencyInsight --configuration runtimeClasspath --dependency org.test:foo to see which project each dependency resolved to.")
            solutions.size() == 2
        }
    }

    def "projects that differ in #difference resolve correctly"() {
        given:
        enableProblemsApiCheck()
        settingsFile.text = """
            rootProject.name = 'root'
            include 'a:foo'
            include '$dependency'
        """
        file("a/foo/build.gradle") << """
            ${javaProject("org.test.a")}
            dependencies {
                implementation project(':$dependency')
            }
            ${resolve.configureProject("runtimeClasspath")}
        """
        file("${dependency.replace(':', '/')}/build.gradle") << javaProject(groupOfDependency)

        when:
        succeeds ":a:foo:checkDeps"

        then: "the dependency selects the project it names"
        resolve.expectGraph(":a:foo") {
            root(":a:foo", "org.test.a:foo:1.0") {
                project(":$dependency", selectedModule)
            }
        }

        and:
        assertNoProjectCoordCollisionProblems()

        where:
        difference | dependency | groupOfDependency | selectedModule
        'group'    | 'b:foo'    | 'org.test.b'      | 'org.test.b:foo:1.0'
        'name'     | 'b:bar'    | 'org.test.a'      | 'org.test.a:bar:1.0'
    }

    def "publishing two projects with colliding coordinates warns about neither"() {
        given:
        enableProblemsApiCheck()
        ["a", "b"].each { container ->
            file("$container/foo/build.gradle") << """
                plugins {
                    id 'java-library'
                    id 'maven-publish'
                }
                group = 'org.test'
                version = '1.0'
                publishing {
                    repositories {
                        maven { url = '${mavenRepo.uri}' }
                    }
                    publications {
                        maven(MavenPublication) { from components.java }
                    }
                }
            """
        }

        when:
        succeeds "publish"

        then: "both projects publish to the very same location"
        file("maven-repo/org/test/foo/1.0/foo-1.0.pom").isFile()

        and: "publishing resolves nothing, so the collision is not seen"
        assertNoProjectCoordCollisionProblems()
    }

    def "a three-way collision is reported once, naming all three projects"() {
        given:
        enableProblemsApiCheck()
        settingsFile << """
            include 'c:foo'
        """
        file("a/foo/build.gradle") << """
            ${javaProject("org.test")}
            dependencies {
                implementation project(':b:foo')
                implementation project(':c:foo')
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """

        file("b/foo/build.gradle") << javaProject("org.test")
        file("c/foo/build.gradle") << javaProject("org.test")

        when:
        succeeds ":a:foo:checkDeps"

        then:
        assertProjectCoordCollisionProblems(collisionWarning(":a:foo:runtimeClasspath", "org.test:foo", ":a:foo", ":b:foo", ":c:foo"))
    }

    def "a collision across builds names the projects by build tree path"() {
        given:
        enableProblemsApiCheck()
        settingsFile << """
            includeBuild 'other'
        """
        file("other/settings.gradle") << """
            rootProject.name = 'other'
            include 'foo'
        """
        file("other/foo/build.gradle") << javaProject("org.test", "1.0")
        file("a/foo/build.gradle") << javaProject("org.test", "1.0")
        file("b/foo/build.gradle") << javaProject("org.unrelated", "1.0")
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer", "1.0")}
            dependencies {
                implementation project(':a:foo')
                implementation 'org.test:foo:1.0'
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then: "the included build's project is named ':other:foo', not ':foo'"
        assertProjectCoordCollisionProblems(collisionWarning(":consumer:runtimeClasspath", "org.test:foo", ":a:foo", ":other:foo"))
    }

    def "substituting an external module with an included build project is not a collision"() {
        given:
        enableProblemsApiCheck()
        settingsFile << """
            includeBuild 'other'
        """
        file("other/settings.gradle") << """
            rootProject.name = 'other'
            include 'foo'
        """
        file("other/foo/build.gradle") << javaProject("org.test", "1.0")
        file("a/foo/build.gradle") << javaProject("org.unrelated", "1.0")
        file("b/foo/build.gradle") << javaProject("org.unrelated.other", "1.0")
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer", "1.0")}
            dependencies {
                implementation 'org.test:foo:1.0'
            }
            ${resolve.configureProject("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then:
        resolve.expectGraph(":consumer") {
            root(":consumer", "org.consumer:consumer:1.0") {
                edge("org.test:foo:1.0", ":other:foo", "org.test:foo:1.0").compositeSubstitute()
            }
        }
        assertNoProjectCoordCollisionProblems()
    }

    def "a project to project substitution rule is not a collision"() {
        given:
        enableProblemsApiCheck()
        file("a/foo/build.gradle") << javaProject("org.a")
        file("b/foo/build.gradle") << javaProject("org.b")
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            configurations.all {
                resolutionStrategy.dependencySubstitution {
                    substitute(project(':a:foo')).using(project(':b:foo'))
                }
            }
            dependencies {
                implementation project(':a:foo')
            }
            ${resolve.configureProject("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then:
        resolve.expectGraph(":consumer") {
            root(":consumer", "org.consumer:consumer:1.0") {
                edge("project ':a:foo'", ":b:foo", "org.b:foo:1.0").selectedByRule()
            }
        }
        assertNoProjectCoordCollisionProblems()
    }

    def "a collision reachable from several projects resolving in parallel is reported once per resolved configuration"() {
        given:
        enableProblemsApiCheck()
        def consumers = (1..2).collect { "consumer$it".toString() }
        settingsFile << consumers.collect { "include '$it'\n" }.join()
        collidingLeaves()
        consumers.each { consumer ->
            file("$consumer/build.gradle") << """
                ${javaProject("org.$consumer")}
                dependencies {
                    implementation project(':a:foo')
                    implementation project(':b:foo')
                }
                ${resolve.configureProjectGraphOnly("runtimeClasspath")}
            """
        }

        when:
        executer.withArgument("--parallel")
        succeeds(consumers.collect { ":$it:checkDeps".toString() })

        then:
        assertProjectCoordCollisionProblems(*consumers.collect {
            collisionWarning(":$it:runtimeClasspath".toString(), "org.test:foo", ":a:foo", ":b:foo")
        })
    }

    def "a collision is reported when a colliding project is reached transitively - #condition"() {
        given: "':bar' brings in ':b:foo', and ':baz' brings in ':a:foo'"
        enableProblemsApiCheck()
        settingsFile << """
            include 'bar'
            include 'baz'
        """
        collidingLeaves()
        file("bar/build.gradle") << """
            ${javaProject("org.bar")}
            dependencies {
                api project(':b:foo')
            }
        """
        file("baz/build.gradle") << """
            ${javaProject("org.baz")}
            dependencies {
                api project(':a:foo')
            }
        """
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            dependencies {
                ${dependencies.join('\n')}
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then: "the colliding projects are named, and the projects that pulled them in are not"
        assertProjectCoordCollisionProblems(collisionWarning(":consumer:runtimeClasspath", "org.test:foo", ":a:foo", ":b:foo"))

        where:
        condition                 | dependencies
        'one side directly'       | ["implementation project(':a:foo')", "implementation project(':bar')"]
        'both sides transitively' | ["implementation project(':baz')", "implementation project(':bar')"]
    }

    def "a collision is reported once per resolved configuration that can see it"() {
        def resolvableConfigurations = [
            "compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath"
        ]

        given:
        enableProblemsApiCheck()
        collidingLeaves()
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            dependencies {
                implementation project(':a:foo')
                implementation project(':b:foo')
            }
            ${resolve.configureProjectGraphOnly(resolvableConfigurations.head(), *resolvableConfigurations.tail())}
        """

        when:
        succeeds(resolvableConfigurations.collect { ":consumer:check${it.capitalize()}".toString() })

        then:
        assertProjectCoordCollisionProblems(*resolvableConfigurations.collect {
            collisionWarning(":consumer:$it".toString(), "org.test:foo", ":a:foo", ":b:foo")
        })
    }

    def "each colliding module is reported separately"() {
        given: "two independent collisions, ':a:foo' with ':b:foo' and ':a:bar' with ':b:bar'"
        enableProblemsApiCheck()
        settingsFile << """
            include 'a:bar'
            include 'b:bar'
        """
        collidingLeaves()
        file("a/bar/build.gradle") << javaProject("org.test")
        file("b/bar/build.gradle") << javaProject("org.test")
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            dependencies {
                implementation project(':a:foo')
                implementation project(':b:foo')
                implementation project(':a:bar')
                implementation project(':b:bar')
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then: "one warning per colliding module, each naming only its own projects"
        assertProjectCoordCollisionProblems(
            collisionWarning(":consumer:runtimeClasspath", "org.test:bar", ":a:bar", ":b:bar"),
            collisionWarning(":consumer:runtimeClasspath", "org.test:foo", ":a:foo", ":b:foo")
        )
    }

    def "a collision with the root project is reported"() {
        given:
        enableProblemsApiCheck()
        settingsFile.text = """
            rootProject.name = 'foo'
            include 'b:foo'
        """
        buildFile << """
            ${javaProject("org.test")}
            dependencies {
                implementation project(':b:foo')
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """
        file("b/foo/build.gradle") << javaProject("org.test")

        when:
        succeeds "checkDeps"

        then:
        assertProjectCoordCollisionProblems(collisionWarning(":runtimeClasspath", "org.test:foo", ":", ":b:foo"))
    }

    def "detection follows the coordinates the projects have when the graph is built - #condition"() {
        given: "':a:foo' is 'org.test:foo', and ':b:foo' gets its group in varying ways"
        enableProblemsApiCheck()

        file("a/foo/build.gradle") << """
            ${javaProject("org.test")}
            dependencies {
                implementation project(':b:foo')
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """
        file("b/foo/build.gradle") << """
            plugins {
                id 'java-library'
            }
            version = '1.0'
            $groupOfB
        """

        when:
        succeeds ":a:foo:checkDeps"

        then:
        resolve.expectGraphOnly(":a:foo") {
            root(":a:foo", "org.test:foo:1.0") {
                edge("project ':b:foo'", selectedProject, selectedModule)
            }
        }

        if (expectedCollisionWarning) {
            assertProjectCoordCollisionProblems(collisionWarning(":a:foo:runtimeClasspath", "org.test:foo", ":a:foo", ":b:foo"))
        } else {
            assertNoProjectCoordCollisionProblems()
        }

        where:
        condition                               | groupOfB                               | selectedProject | selectedModule     | expectedCollisionWarning
        'clashing group in the build script'    | "group = 'org.test'"                   | ':a:foo'        | 'org.test:foo:1.0' | true
        'clashing group set in afterEvaluate'   | "afterEvaluate { group = 'org.test' }" | ':a:foo'        | 'org.test:foo:1.0' | true
        'distinct group set in afterEvaluate'   | "afterEvaluate { group = 'org.b' }"    | ':b:foo'        | 'org.b:foo:1.0'    | false
        'no group, so the default path applies' | ""                                     | ':b:foo'        | 'root.b:foo:1.0'   | false
    }

    def "projects that differ only in version are reported"() {
        given: "':a:foo' and ':b:foo' share a group and a name, but not a version"
        enableProblemsApiCheck()
        file("a/foo/build.gradle") << javaProject("org.test", "1.0")
        file("b/foo/build.gradle") << javaProject("org.test", "2.0")
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            dependencies {
                implementation project(':a:foo')
                implementation project(':b:foo')
            }
            ${resolve.configureProject("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then: "the dependency on ':a:foo' resolves to ':b:foo' - the symptom of #847"
        resolve.expectGraph(":consumer") {
            root(":consumer", "org.consumer:consumer:1.0") {
                edge("project ':a:foo'", ":b:foo", "org.test:foo:2.0").byConflictResolution("between versions 1.0 and 2.0")
                project(":b:foo", "org.test:foo:2.0")
            }
        }

        and: "conflict resolution picked between the two rather than merging them, and it is still reported"
        assertProjectCoordCollisionProblems(collisionWarning(":consumer:runtimeClasspath", "org.test:foo", ":a:foo", ":b:foo"))
    }

    def "no warning when the colliding projects do not meet in the graph - #condition"() {
        given: "':a:foo' and ':b:foo' collide, and a consumer that resolves something else"
        enableProblemsApiCheck()
        collidingLeaves()
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            dependencies {
                $dependencies
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then: "the ambiguity is a property of a resolved graph, not of the build's layout"
        assertNoProjectCoordCollisionProblems()

        where:
        condition                         | dependencies
        'nothing depends on either'       | ""
        'only one of them is depended on' | "implementation project(':a:foo')"
    }

    def "no warning when each colliding project is reached by a different consumer"() {
        given: "both projects are resolved in this build, but never within one graph"
        enableProblemsApiCheck()
        settingsFile << """
            include 'consumer1'
            include 'consumer2'
        """
        collidingLeaves()
        [consumer1: ":a:foo", consumer2: ":b:foo"].each { consumer, target ->
            file("$consumer/build.gradle") << """
                ${javaProject("org.$consumer")}
                dependencies {
                    implementation project('$target')
                }
                ${resolve.configureProject("runtimeClasspath")}
            """
        }

        when:
        succeeds ":consumer1:checkDeps", ":consumer2:checkDeps"

        then: "neither resolution is ambiguous, so neither reports"
        resolve.expectGraph(":consumer1") {
            root(":consumer1", "org.consumer1:consumer1:1.0") {
                project(":a:foo", "org.test:foo:1.0")
            }
        }
        resolve.expectGraph(":consumer2") {
            root(":consumer2", "org.consumer2:consumer2:1.0") {
                project(":b:foo", "org.test:foo:1.0")
            }
        }

        and:
        assertNoProjectCoordCollisionProblems()
    }

    def "a configuration resolved twice in one build warns once"() {
        given: "resolving artifacts makes the classpath a task input, so the graph is resolved for the task graph and again at execution"
        enableProblemsApiCheck()
        collidingLeaves()
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            dependencies {
                implementation project(':a:foo')
                implementation project(':b:foo')
            }
            ${resolve.configureProject("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then: "the reporter warns on both passes, and the Problems API collapses the identical warnings"
        def collisions = drainCollisionProblems()
        collisions.size() == 1
        collisions.first().contextualLabel == collisionWarning(":consumer:runtimeClasspath", "org.test:foo", ":a:foo", ":b:foo")
    }

    def "a collision reachable only from a detached configuration is reported"() {
        given:
        enableProblemsApiCheck()
        collidingLeaves()
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            def detached = configurations.detachedConfiguration(
                dependencies.project(':a:foo'),
                dependencies.project(':b:foo')
            )
            def files = detached.incoming.files
            tasks.register('resolveDetached') {
                def resolved = files
                doLast {
                    println "FILES: " + resolved.files.name.sort()
                }
            }
        """

        when:
        succeeds ":consumer:resolveDetached"

        then: "the detached configuration is named by the project that created it"
        verifyAll(receivedProblem) {
            fqid == COLLISION_PROBLEM_FQID
            contextualLabel == collisionWarning(":consumer:detachedConfiguration1", "org.test:foo", ":a:foo", ":b:foo")

            and: "a detached configuration cannot be named on the command line, so no report is suggested"
            !details.contains("dependencyInsight")
        }
    }

    def "a collision between the test fixtures of two projects is reported"() {
        given: "test fixtures derive a capability from the coordinates, so those collide too"
        enableProblemsApiCheck()
        ["a", "b"].each { container ->
            file("$container/foo/build.gradle") << """
                plugins {
                    id 'java-library'
                    id 'java-test-fixtures'
                }
                group = 'org.test'
                version = '1.0'
            """
        }
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            dependencies {
                testImplementation testFixtures(project(':a:foo'))
                testImplementation testFixtures(project(':b:foo'))
            }
            ${resolve.configureProjectGraphOnly("testRuntimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then:
        assertProjectCoordCollisionProblems(collisionWarning(":consumer:testRuntimeClasspath", "org.test:foo", ":a:foo", ":b:foo"))
    }

    def "an included plugin build does not produce a spurious collision"() {
        given:
        enableProblemsApiCheck()
        settingsFile.text = """
            pluginManagement {
                includeBuild 'plugin-build'
            }
            rootProject.name = 'root'
            include 'a:foo'
            include 'b:foo'
        """
        file("plugin-build/settings.gradle") << """
            rootProject.name = 'plugin-build'
            include 'foo'
        """
        file("plugin-build/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
            group = 'org.test'
        """
        file("plugin-build/src/main/groovy/my-plugin.gradle") << """
            println 'my-plugin applied'
        """
        file("plugin-build/foo/build.gradle") << javaProject("org.test")
        file("a/foo/build.gradle") << """
            plugins {
                id 'java-library'
                id 'my-plugin'
            }
            group = 'org.test'
            version = '1.0'
            dependencies {
                implementation project(':b:foo')
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """
        file("b/foo/build.gradle") << javaProject("org.test")

        when:
        succeeds ":a:foo:checkDeps"

        then: "the collision within the main build is reported, and the plugin build adds nothing"
        assertProjectCoordCollisionProblems(collisionWarning(":a:foo:runtimeClasspath", "org.test:foo", ":a:foo", ":b:foo"))
    }

    @Issue("https://github.com/gradle/gradle/issues/12315")
    def "a project and an external module with the same coordinates are not reported, and the newest version wins"() {
        given: "':a:foo' is 'org.test:foo', and so is a newer module in the repository"
        enableProblemsApiCheck()
        projectAndModuleWithTheSameCoordinates(false)

        when:
        succeeds ":consumer:checkDeps"

        then: "the dependency on the project is redirected to the module"
        resolve.expectGraph(":consumer") {
            root(":consumer", "org.consumer:consumer:1.0") {
                edge("project ':a:foo'", "org.test:foo:2.0")
                module("org.test:foo:2.0").byConflictResolution("between versions 1.0 and 2.0")
            }
        }

        and: "only projects colliding with projects are reported"
        assertNoProjectCoordCollisionProblems()
    }

    @Issue("https://github.com/gradle/gradle/issues/12315")
    def "a project and an external module with the same coordinates are not reported, and preferProjectModules keeps the project"() {
        given: "':a:foo' is 'org.test:foo', and so is a newer module in the repository"
        enableProblemsApiCheck()
        projectAndModuleWithTheSameCoordinates(true)

        when:
        succeeds ":consumer:checkDeps"

        then: "the dependency on the module is redirected to the project"
        resolve.expectGraph(":consumer") {
            root(":consumer", "org.consumer:consumer:1.0") {
                project(":a:foo", "org.test:foo:1.0").byConflictResolution("between versions 1.0 and 2.0")
                edge("org.test:foo:2.0", ":a:foo", "org.test:foo:1.0")
            }
        }

        and: "only projects colliding with projects are reported"
        assertNoProjectCoordCollisionProblems()
    }

    def "external modules sharing a capability do not produce a collision"() {
        given:
        enableProblemsApiCheck()
        mavenRepo.module("org.test", "foo", "2.0").publish()
        mavenRepo.module("org.test", "foo-alias", "1.0").publish()
        settingsFile << """
            dependencyResolutionManagement {
                repositories { maven { url = '${mavenRepo.uri}' } }
            }
        """
        file("a/foo/build.gradle") << javaProject("org.a")
        file("b/foo/build.gradle") << javaProject("org.b")
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            dependencies {
                components {
                    withModule('org.test:foo-alias') { details ->
                        details.allVariants {
                            withCapabilities {
                                addCapability('org.test', 'foo', '1.0')
                            }
                        }
                    }
                }
                implementation 'org.test:foo:2.0'
                implementation 'org.test:foo-alias:1.0'
            }
            configurations.all {
                resolutionStrategy.capabilitiesResolution.withCapability('org.test:foo') {
                    selectHighestVersion()
                }
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """

        when:
        succeeds ":consumer:checkDeps"

        then: "only projects can collide, and a capability conflict between modules is not one"
        assertNoProjectCoordCollisionProblems()
    }

    /**
     * A project and a repository module that share coordinates, consumed together.
     */
    private void projectAndModuleWithTheSameCoordinates(boolean preferProjectModules) {
        mavenRepo.module("org.test", "foo", "2.0").publish()
        settingsFile << """
            dependencyResolutionManagement {
                repositories { maven { url = '${mavenRepo.uri}' } }
            }
        """
        file("a/foo/build.gradle") << javaProject("org.test", "1.0")
        file("b/foo/build.gradle") << javaProject("org.unrelated", "1.0")
        settingsFile << "include 'consumer'\n"
        file("consumer/build.gradle") << """
            ${javaProject("org.consumer")}
            ${preferProjectModules ? "configurations.all { resolutionStrategy.preferProjectModules() }" : ""}
            dependencies {
                implementation project(':a:foo')
                implementation 'org.test:foo:2.0'
            }
            ${resolve.configureProject("runtimeClasspath")}
        """
    }

    /**
     * Two projects named {@code foo}, under different containers, where {@code :a:foo} depends on
     * {@code :b:foo}. With equal groups their module coordinates collide.
     */
    private void collidingProjects() {
        file("a/foo/build.gradle") << """
            ${javaProject("org.test")}
            dependencies {
                implementation project(':b:foo')
            }
            ${resolve.configureProjectGraphOnly("runtimeClasspath")}
        """
        file("b/foo/build.gradle") << javaProject("org.test")
    }

    /**
     * As {@link #collidingProjects}, but neither project depends on the other, so a consumer is
     * needed to bring them into one graph.
     */
    private void collidingLeaves() {
        file("a/foo/build.gradle") << javaProject("org.test")
        file("b/foo/build.gradle") << javaProject("org.test")
    }

    private static String javaProject(String group, String version = "1.0") {
        """
            plugins {
                id 'java-library'
            }
            group = '$group'
            version = '$version'
        """
    }

    /**
     * Asserts which collisions were reported, not how many times each was: a configuration can be
     * resolved more than once in a build, and identical warnings are collapsed by the Problems API.
     */
    private void assertProjectCoordCollisionProblems(String... expectedMessages) {
        def reported = drainCollisionProblems()
            .collect { it.contextualLabel }
            .unique()

        assert reported.sort() == (expectedMessages as List).sort()
    }

    private void assertNoProjectCoordCollisionProblems() {
        assertProjectCoordCollisionProblems()
    }

    /**
     * The collision problems received so far, marked as validated. Other problems are left alone,
     * so that the cleanup check still fails on a problem no test asserted on.
     */
    private List<ReceivedProblem> drainCollisionProblems() {
        def indices = (0..<receivedProblems.size()).findAll { receivedProblems[it]?.fqid == COLLISION_PROBLEM_FQID }
        return indices.collect { receivedProblem(it) }
    }

    private static String collisionWarning(String configuration, String moduleId, String... identityPaths) {
        def projects = identityPaths.join(", ")
        "Configuration '$configuration' resolves projects $projects as the same module '$moduleId'."
    }
}
