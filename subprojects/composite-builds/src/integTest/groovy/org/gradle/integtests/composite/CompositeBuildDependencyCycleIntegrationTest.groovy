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
import spock.lang.Issue

/**
 * Tests for resolving dependency cycles in a composite build.
 */
class CompositeBuildDependencyCycleIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC
    ResolveTestFixture resolve

    def getCommon() {
        """
            plugins {
                id("java-library")
            }

            // Add additional configurations that do not bring in build dependencies,
            // allowing us to verify the structure of the dependency graph without making
            // the task graph cycle checker angry.
            configurations {
                resolvable("graph") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "no-build-deps"))
                    }
                }
                consumable("graphElements") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "no-build-deps"))
                    }
                    outgoing.artifact(file("\${project.name}-\${version}.jar"))
                }
            }
        """
    }

    def setup() {
        resolve = new ResolveTestFixture(buildA)

        // AbstractCompositeBuildIntegrationTest automatically adds content to the buildA build file,
        // preventing us from using the plugins block.
        buildA.buildFile.text = """
            ${common}
            group = 'org.test'
            version = '1.0'
            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                }
            }
            ${resolve.configureProject("graph", "runtimeClasspath")}
        """

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            [delegate, project("b1"), project("b2")].each { p ->
                p.buildFile << common
            }
        }
        includedBuilds << buildB

        buildC = singleProjectBuild("buildC") {
            buildFile << common
        }
        includedBuilds << buildC
    }

    def "direct dependency cycle between included builds"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"
        dependency buildC, "org.test:buildB:1.0"

        when:
        resolveSucceeds(":checkGraph")

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                edge("org.test:buildC:1.0", ":buildC", "org.test:buildC:1.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                    edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:1.0") {
                        configuration = "runtimeElements"
                        compositeSubstitute()
                    }
                }
            }
        }

        when:
        resolveFails(":checkRuntimeClasspath")

        then:
        failure.assertHasDescription("""Circular dependency between the following tasks:
:buildB:compileJava
\\--- :buildC:compileJava
     \\--- :buildB:compileJava (*)""")
    }

    def "indirect dependency cycle between included builds"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"
        dependency buildC, "org.test:buildD:1.0"

        def buildD = singleProjectBuild("buildD") {
            buildFile << """
                $common
                dependencies {
                    implementation "org.test:buildB:1.0"
                }
            """
        }
        includedBuilds << buildD

        when:
        resolveSucceeds(":checkGraph")

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                edge("org.test:buildC:1.0", ":buildC", "org.test:buildC:1.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                    edge("org.test:buildD:1.0", ":buildD", "org.test:buildD:1.0") {
                        configuration = "runtimeElements"
                        compositeSubstitute()
                        edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:1.0") {
                            configuration = "runtimeElements"
                            compositeSubstitute()
                        }
                    }
                }
            }
        }

        when:
        resolveFails(":checkRuntimeClasspath")

        then:
        failure.assertHasDescription("""Circular dependency between the following tasks:
:buildB:compileJava
\\--- :buildC:compileJava
     \\--- :buildD:compileJava
          \\--- :buildB:compileJava (*)""")
    }

    // Not actually a cycle, just documenting behaviour
    def "dependency cycle between different projects of included builds"() {
        given:
        dependency "org.test:b1:1.0"
        buildB.project("b1").buildFile << """
            dependencies {
                implementation "org.test:buildC:1.0"
            }
        """
        dependency buildC, "org.test:b2:1.0"

        when:
        resolveSucceeds(":checkGraph")

        then:
        checkGraph {
            edge("org.test:b1:1.0", ":buildB:b1", "org.test:b1:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                edge("org.test:buildC:1.0", ":buildC", "org.test:buildC:1.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                    edge("org.test:b2:1.0", ":buildB:b2", "org.test:b2:1.0") {
                        configuration = "runtimeElements"
                        compositeSubstitute()
                    }
                }
            }
        }

        and:
        resolveSucceeds(":checkRuntimeClasspath")
    }

    def "compile-only dependency cycle between included builds"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"
        buildC.buildFile << """
            dependencies {
                compileOnly "org.test:buildB:1.0"
            }
        """

        when:
        resolveSucceeds(":checkGraph")

        then: // No cycle when building dependency graph
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                edge("org.test:buildC:1.0", ":buildC", "org.test:buildC:1.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                }
            }
        }

        when:
        resolveFails(":checkRuntimeClasspath")

        then:
        failure.assertHasDescription("""Circular dependency between the following tasks:
:buildB:compileJava
\\--- :buildC:compileJava
     \\--- :buildB:compileJava (*)""")
    }

    def "dependency cycle between subprojects in an included multiproject build"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            dependencies {
                implementation "org.test:b1:1.0"
            }
        """
        buildB.project("b1").buildFile << """
            dependencies {
                implementation "org.test:b2:1.0"
            }
        """

        buildB.project("b2").buildFile << """
            dependencies {
                implementation "org.test:b1:1.0"
            }
        """

        when:
        resolveSucceeds(":checkGraph")

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:1.0") {
                configuration = "runtimeElements"
                compositeSubstitute()
                edge("org.test:b1:1.0", ":buildB:b1", "org.test:b1:1.0") {
                    configuration = "runtimeElements"
                    compositeSubstitute()
                    edge("org.test:b2:1.0", ":buildB:b2", "org.test:b2:1.0") {
                        configuration = "runtimeElements"
                        compositeSubstitute()
                        edge("org.test:b1:1.0", ":buildB:b1", "org.test:b1:1.0") {}
                    }
                }
            }
        }

        when:
        resolveFails(":checkRuntimeClasspath")

        then:
        failure.assertHasDescription("""Circular dependency between the following tasks:
:buildB:b1:compileJava
\\--- :buildB:b2:compileJava
     \\--- :buildB:b1:compileJava (*)""")
    }

    @Issue("https://github.com/gradle/gradle/issues/6229")
    def "cross-build dependencies without task cycle"() {
        given:
        def buildD = multiProjectBuild("buildD", ['buildD-api', 'buildD-impl']) {
            [delegate, project("buildD-api"), project("buildD-impl")].each { p ->
                p.buildFile << common
            }
            project("buildD-impl").buildFile << """
                dependencies {
                    api(project(":buildD-api"))
                    implementation("org.test:buildE-api:1.0")
                }
            """
        }
        includedBuilds << buildD

        def buildE = multiProjectBuild("buildE", ['buildE-api', 'buildE-impl']) {
            [delegate, project("buildE-api"), project("buildE-impl")].each { p ->
                p.buildFile << common
            }
            project("buildE-impl").buildFile << """
                dependencies {
                    api(project(":buildE-api"))
                    implementation("org.test:buildD-api:1.0")
                }
            """
        }
        includedBuilds << buildE

        when:
        dependency(buildA, "org.test:buildD-impl:1.0")
        dependency(buildA, "org.test:buildE-impl:1.0")

        then:
        resolveSucceeds(":build")

        assertTaskExecuted(":buildD", ":buildD-api:jar")
        assertTaskExecuted(":buildE", ":buildE-api:jar")
        assertTaskExecuted(":buildD", ":buildD-impl:jar")
        assertTaskExecuted(":buildE", ":buildE-impl:jar")
        assertTaskExecuted(":", ":jar")
    }

    def "cross-build resolve jars without task cycle"() {
        given:
        buildA.buildFile << """
            task resolveJars {
                dependsOn gradle.includedBuild('buildB').task(':b1:resolveJars')
            }
        """
        buildB.project("b1").buildFile << """
            dependencies {
                implementation "org.test:buildC:1.0"
            }
            task resolveJars(type: Copy) {
                from configurations.runtimeClasspath
                into "\$buildDir/jars"
            }
        """
        dependency buildC, "org.test:b2:1.0"

        when:
        resolveSucceeds(':resolveJars')

        then:
        assertTaskExecuted(':buildB', ":b2:jar")
        assertTaskExecuted(':buildC', ":jar")
        assertTaskExecuted(':buildB', ":b1:resolveJars")
        assertTaskExecuted(':', ":resolveJars")
    }

    def "direct dependsOn cycle between builds including one another"() {
        given:
        buildA.buildFile << """
            task a {
                dependsOn gradle.includedBuild('buildB').task(':b')
            }
        """
        buildB.buildFile << """
            task b {
                dependsOn gradle.includedBuild('buildC').task(':c')
            }
        """
        buildB.settingsFile << """
            includeBuild('../buildC')
        """
        buildC.buildFile << """
            task c {
                dependsOn gradle.includedBuild('buildB').task(':b')
            }
        """
        buildC.settingsFile << """
            includeBuild('../buildB')
        """

        when:
        resolveFails(":a")

        then:
        failure.assertHasDescription("""Circular dependency between the following tasks:
:buildB:b
\\--- :buildC:c
     \\--- :buildB:b (*)""")
    }

    def "declaring a dependency on the resolving project's module coordinates without a substitution is deprecated"() {
        given:
        dependency(buildA, "org.test:buildB:1.0")
        dependency(buildB, "org.test:buildA:1.0")

        buildA.buildFile << """
            ${mavenTestRepository()}
        """

        when:
        executer.expectDocumentedDeprecationWarning("Depending on the resolving project's module coordinates has been deprecated. This will fail with an error in Gradle 10. Use a project dependency instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#module_identity_for_root_component")
        resolveSucceeds(":checkGraph")

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:1.0") {
                compositeSubstitute()
                edge("org.test:buildA:1.0", ":buildA", "org.test:buildA:1.0")
            }
        }
    }

    def "declaring a dependency on the resolving project's module coordinates with a substitution succeeds"() {
        given:
        dependency(buildA, "org.test:buildB:1.0")
        dependency(buildB, "org.test:buildA:1.0")

        buildA.buildFile << """
            ${mavenTestRepository()}

            configurations.configureEach {
                resolutionStrategy.dependencySubstitution {
                    substitute(module('org.test:buildA:1.0')).using(project(':'))
                }
            }
        """

        when:
        resolveSucceeds(":checkGraph")

        then:
        checkGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:1.0") {
                compositeSubstitute()
                edge("org.test:buildA:1.0", ":buildA", "org.test:buildA:1.0")
            }
        }
    }

    protected void resolveSucceeds(String task) {
        execute(buildA, task)
    }

    protected void resolveFails(String task) {
        fails(buildA, task)
    }


    void checkGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.expectGraph(":") {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
