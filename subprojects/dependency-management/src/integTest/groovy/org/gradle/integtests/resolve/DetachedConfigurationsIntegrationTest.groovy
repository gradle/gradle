/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Issue

@FluidDependenciesResolveTest
class DetachedConfigurationsIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2889")
    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "detached configurations may have separate dependencies"() {
        given:
        settingsFile << "include 'a', 'b'"
        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()

        buildFile << """
            allprojects {
                configurations {
                    foo
                }
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                task checkDependencies {
                    doLast {
                        configurations.each { conf ->
                            def declared = conf.dependencies
                            def detached = project.configurations.detachedConfiguration(declared as Dependency[])
                            def resolved = detached.resolvedConfiguration.getFirstLevelModuleDependencies()
                            assert declared*.name == resolved*.moduleName
                        }
                    }
                }
            }
            project(":a") {
                dependencies {
                    foo "org:foo:1.0"
                }
            }
            project(":b") {
                dependencies {
                    foo "org:bar:1.0"
                }
            }
        """

        expect:
        run "checkDependencies"
    }

    def "detached configurations may have dependencies on other projects"() {
        given:
        settingsFile << "include 'other'"
        buildFile << """
            plugins {
                id 'java-library'
            }
            
            def detached = project.configurations.detachedConfiguration()
            detached.dependencies.add(project.dependencies.create(project(':other')))
           
            task checkDependencies {
                doLast {
                    assert detached.resolvedConfiguration.getFirstLevelModuleDependencies().moduleName.contains('other')
                    assert detached.resolvedConfiguration.resolvedArtifacts.collect { it.file.name }.contains("other.jar")
                }
            }
        """

        file("other/build.gradle") << """
            plugins {
                id 'java-library'
            }
        """

        expect:
        run "checkDependencies"
    }

    // This behavior will be removed in Gradle 9.0
    @Deprecated
    def "detached configurations can contain artifacts and resolve them during a self-dependency scenario"() {
        given:
        settingsFile << """
            rootProject.name = 'test'
        """

        buildFile << """
            plugins {
                id 'java-library'
            }
            
            def detached = project.configurations.detachedConfiguration()
            detached.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            detached.dependencies.add(project.dependencies.create(project))
            
            task makeArtifact(type: Zip) {
                archiveFileName = "artifact.zip"
                from "artifact.txt"
            }
            
            detached.outgoing.artifact(tasks.makeArtifact)
           
            task checkDependencies {
                doLast {
                    assert detached.resolvedConfiguration.getFirstLevelModuleDependencies().moduleName.contains('test')
                    assert detached.resolvedConfiguration.resolvedArtifacts.collect { it.file.name }.contains("artifact.zip")
                }
            }
        """

        file("artifact.txt") << "sample artifact"

        expect:
        run "checkDependencies"
    }
}
