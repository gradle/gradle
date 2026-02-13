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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest

@FluidDependenciesResolveTest
class FilteredConfigurationIntegrationTest extends AbstractDependencyResolutionTest {

    def "can query files for filtered first level dependencies"() {
        mavenRepo.module("group", "test1", "1.0").publish()
        mavenRepo.module("group", "test2", "1.0").publish()

        settingsFile << """
            rootProject.name = "main"
            include "child1", "child2"
            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """

        buildFile << """
            configurations {
                compile
                create('default') { extendsFrom compile }
            }
            artifacts {
                compile file("main.jar")
            }
            dependencies {
                compile files("lib.jar")
                compile "group:test1:1.0"
                compile project(':child1')
                compile project(':child2')
            }

            task verify {
                def fileDeps = configurations.compile.incoming.artifactView { componentFilter { !(it instanceof ModuleComponentIdentifier) && !(it instanceof ProjectComponentIdentifier) } }.files
                def externalDeps = configurations.compile.incoming.artifactView { componentFilter { it instanceof ModuleComponentIdentifier } }.files
                def child1Deps = configurations.compile.incoming.artifactView { componentFilter { it instanceof ProjectComponentIdentifier && it.projectPath == ':child1' } }.files

                def basic = configurations.compile
                def artifactView = configurations.compile.incoming.artifactView {}.files
                def basicIncoming = configurations.compile.incoming.files

                doLast {
                    println "file-dependencies: " + fileDeps.files.collect { it.name }
                    println "external-dependencies: " + externalDeps.files.collect { it.name }
                    println "child1-dependencies: " + child1Deps.files.collect { it.name }

                    assert basicIncoming.files == basic.files
                    assert artifactView.files == basic.files
                }
            }
        """

        file("child1/build.gradle") << """
            configurations {
                compile
                create('default') { extendsFrom compile }
            }
            artifacts {
                compile file("child1.jar")
            }
            dependencies {
                compile files("child1-lib.jar")
                compile "group:test2:1.0"
            }
        """

        file("child2/build.gradle") << """
            configurations {
                compile
                create('default') { extendsFrom compile }
            }
            artifacts {
                compile file("child2.jar")
            }
        """

        when:
        succeeds("verify")

        then:
        outputContains("file-dependencies: [lib.jar, child1-lib.jar]")
        outputContains("external-dependencies: [test1-1.0.jar, test2-1.0.jar]")
        outputContains("child1-dependencies: [child1.jar]")
    }

    def "can query files for filtered first level dependencies when there is a cycle in the dependency graph"() {
        mavenRepo.module("group", "test1", "1.0").publish()
        mavenRepo.module("group", "test2", "1.0").publish()

        settingsFile << """
            rootProject.name = "main"
            include "child1"
            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """
        buildFile << """
            configurations {
                compile
                create('default') { extendsFrom compile }
            }
            dependencies {
                compile files("lib.jar")
                compile "group:test1:1.0"
                compile project(':child1')
            }
            artifacts {
                compile file("main.jar")
            }

            task verify {
                def externalDeps = configurations.compile.incoming.artifactView { componentFilter { it instanceof ModuleComponentIdentifier } }.files
                def child1Deps = configurations.compile.incoming.artifactView { componentFilter { it instanceof ProjectComponentIdentifier && it.projectPath == ':child1' } }.files

                def basic = configurations.compile
                def artifactView = configurations.compile.incoming.artifactView {}.files
                def basicIncoming = configurations.compile.incoming.files

                doLast {
                    println "external-dependencies: " + externalDeps.collect { it.name }
                    println "child1-dependencies: " + child1Deps.collect { it.name }

                    assert basicIncoming.files == basic.files
                    assert artifactView.files == basic.files
                }
            }
        """

        file("child1/build.gradle") << """
            configurations {
                compile
                create('default') { extendsFrom compile }
            }
            artifacts {
                compile file("child1.jar")
            }
            dependencies {
                compile files("child1-lib.jar")
                compile "group:test2:1.0"
                compile project(":")
            }
        """

        when:
        succeeds("verify")

        then:
        outputContains("external-dependencies: [test1-1.0.jar, test2-1.0.jar]")
        outputContains("child1-dependencies: [child1.jar]")
    }

    // Note: this captures existing behaviour (all files are built) rather than desired behaviour (only those files reachable from selected deps are built)
    def "can use filtered configuration as task input"() {
        settingsFile << """
            rootProject.name = "main"
            include "child1"
        """
        buildFile << """
            configurations {
                compile
                create('default') { extendsFrom compile }
            }
            task jar {
                outputs.file file("\${project.name}.jar")
            }
            task lib {
                outputs.file file("\${project.name}-lib.jar")
            }
            artifacts {
                compile file: tasks.jar.outputs.files.singleFile, builtBy: tasks.jar
            }
            dependencies {
                compile tasks.lib.outputs.files
                compile project(':child1')
            }

            task verify {
                inputs.files configurations.compile.incoming.artifactView { componentFilter { it instanceof ProjectComponentIdentifier } }.files
            }
        """

        file("child1/build.gradle") << """
            configurations {
                compile
                create('default') { extendsFrom compile }
            }
            task jar {
                outputs.file file("\${project.name}.jar")
            }
            task lib {
                outputs.file file("\${project.name}-lib.jar")
            }
            artifacts {
                compile file: tasks.jar.outputs.files.singleFile, builtBy: tasks.jar
            }
            dependencies {
                compile tasks.lib.outputs.files
            }
        """

        when:
        succeeds("verify")

        then:
        // Should not be including ':lib' as it's not required
        result.assertTasksScheduled(":lib", ":child1:jar", ":child1:lib", ":verify")
    }

}
