/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ResolvedConfigurationBuildOperationIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "resolved dependencies are exposed via build operation"() {
        setup:
        buildFile << """                
            allprojects {
                apply plugin: "java"
                repositories {
                    maven { url '${mavenHttpRepo.uri}' }
                }
            }
            dependencies {
                compile 'org.foo:hiphop:1.0'
                compile 'org.foo:unknown:1.0' //does not exist
                compile project(":child")
                compile 'org.foo:rock:1.0' //contains unresolved transitive dependency
            }

            task resolve(type: Copy) {
                from configurations.compile
                into "build/resolved"
            }
        """
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown');
        def m3 = mavenHttpRepo.module('org.foo', 'broken');
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        m1.allowAll()
        m2.allowAll()
        m3.pom.expectGetBroken()
        m4.allowAll()

        when:
        fails "resolve"

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationPath == ":compile"
        op.details.buildPath == ":"
        op.details.configurationDescription ==~ /Dependencies for source set 'main'.*/
        op.details.configurationVisible == false
        op.details.configurationTransitive == true

        op.result.resolvedDependenciesCount == 4
    }

    def "resolved dependencies in composite builds are exposed via build operation"() {
        setup:
        def m1 = mavenHttpRepo.module('org.foo', 'app-dep').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'root-dep').publish()

        file("my-composite-app/src/main/java/App.java") << "public class App {}"
        file("my-composite-app/build.gradle") << """
            group = "org.foo"
            version = '1.0'

            apply plugin: "java"
            repositories {
                maven { url '${mavenHttpRepo.uri}' }
            }
            
            dependencies {
                compile 'org.foo:app-dep:1.0'
            }
        """

        file("my-composite-app/settings.gradle") << "rootProject.name = 'my-composite-app'"
        buildFile << """                
            allprojects {
                apply plugin: "java"
                repositories {
                    maven { url '${mavenHttpRepo.uri}' }
                }
            }
            dependencies {
                compile 'org.foo:root-dep:1.0'
                compile 'org.foo:my-composite-app:1.0'
            }

            task resolve(type: Copy) {
                from configurations.compile
                into "build/resolved"
            }
        """
        settingsFile << """
        rootProject.name='root'
        includeBuild 'my-composite-app'
        """

        m1.allowAll()
        m2.allowAll()

        when:
        run "resolve"

        then:
        def resolveOperations = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        resolveOperations.size() == 2
        resolveOperations[0].details.configurationPath == ":compile"
        resolveOperations[0].details.buildPath == ":"
        resolveOperations[0].details.configurationDescription ==~ /Dependencies for source set 'main'.*/
        resolveOperations[0].details.configurationVisible == false
        resolveOperations[0].details.configurationTransitive == true
        resolveOperations[0].result.resolvedDependenciesCount == 2

        resolveOperations[1].details.configurationPath == ":compileClasspath"
        resolveOperations[1].details.buildPath == ":my-composite-app"
        resolveOperations[1].details.configurationDescription == "Compile classpath for source set 'main'."
        resolveOperations[1].details.configurationVisible == false
        resolveOperations[1].details.configurationTransitive == true
        resolveOperations[1].result.resolvedDependenciesCount == 1
    }

    def "resolved dependencies from detached configurations are exposed"() {
        setup:
        buildFile << """                
        repositories {
            maven { url '${mavenHttpRepo.uri}' }
        }
        
        task resolve {
            doLast {
                project.configurations.detachedConfiguration(dependencies.create('org.foo:dep:1.0')).files
            }
        }
        """
        def m1 = mavenHttpRepo.module('org.foo', 'dep').publish()


        m1.allowAll()

        when:
        run "resolve"

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationPath == ":detachedConfiguration1"
        op.details.buildPath == ":"
        op.details.configurationDescription == null
        op.details.configurationVisible == true
        op.details.configurationTransitive == true

        op.result.resolvedDependenciesCount == 1
    }

}
