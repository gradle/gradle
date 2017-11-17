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
import spock.lang.Unroll

class ResolveConfigurationDependenciesBuildOperationIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "resolved configurations are exposed via build operation"() {
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
        op.details.configurationName == "compile"
        op.details.projectPath == ":"
        op.details.buildPath == ":"
        op.details.scriptConfiguration == false
        op.details.configurationDescription ==~ /Dependencies for source set 'main'.*/
        op.details.configurationVisible == false
        op.details.configurationTransitive == true

        op.result.resolvedDependenciesCount == 4
    }

    def "resolved detached configurations are exposed"() {
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
        op.details.configurationName == "detachedConfiguration1"
        op.details.projectPath == ":"
        op.details.scriptConfiguration == false
        op.details.buildPath == ":"
        op.details.configurationDescription == null
        op.details.configurationVisible == true
        op.details.configurationTransitive == true

        op.result.resolvedDependenciesCount == 1
    }

    def "resolved configurations in composite builds are exposed via build operation"() {
        setup:
        def m1 = mavenHttpRepo.module('org.foo', 'app-dep').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'root-dep').publish()

        setupComposite()
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


        m1.allowAll()
        m2.allowAll()

        when:
        run "resolve"

        then: "configuration of composite are exposed"
        def resolveOperations = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        resolveOperations.size() == 2
        resolveOperations[0].details.configurationName == "compile"
        resolveOperations[0].details.projectPath == ":"
        resolveOperations[0].details.buildPath == ":"
        resolveOperations[0].details.scriptConfiguration == false
        resolveOperations[0].details.configurationDescription ==~ /Dependencies for source set 'main'.*/
        resolveOperations[0].details.configurationVisible == false
        resolveOperations[0].details.configurationTransitive == true
        resolveOperations[0].result.resolvedDependenciesCount == 2

        and: "classpath configuration is exposed"
        resolveOperations[1].details.configurationName == "compileClasspath"
        resolveOperations[1].details.projectPath == ":"
        resolveOperations[1].details.buildPath == ":my-composite-app"
        resolveOperations[1].details.scriptConfiguration == false
        resolveOperations[1].details.configurationDescription == "Compile classpath for source set 'main'."
        resolveOperations[1].details.configurationVisible == false
        resolveOperations[1].details.configurationTransitive == true
        resolveOperations[1].result.resolvedDependenciesCount == 1
    }

    def "resolved configurations of composite builds as build dependencies are exposed"() {
        setup:
        def m1 = mavenHttpRepo.module('org.foo', 'root-dep').publish()
        setupComposite()
        buildFile << """                
            buildscript {
                repositories {
                    maven { url '${mavenHttpRepo.uri}' }
                }
                dependencies {
                    classpath 'org.foo:root-dep:1.0'
                    classpath 'org.foo:my-composite-app:1.0'
                }
            }
            
            apply plugin: "java"
        """


        m1.allowAll()

        when:
        run "buildEnvironment"

        then:
        def resolveOperations = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        resolveOperations.size() == 2
        resolveOperations[0].details.configurationName == "compileClasspath"
        resolveOperations[0].details.projectPath == ":"
        resolveOperations[0].details.buildPath == ":my-composite-app"
        resolveOperations[0].details.scriptConfiguration == false
        resolveOperations[0].details.configurationDescription == "Compile classpath for source set 'main'."
        resolveOperations[0].details.configurationVisible == false
        resolveOperations[0].details.configurationTransitive == true
        resolveOperations[0].result.resolvedDependenciesCount == 1

        resolveOperations[1].details.configurationName == "classpath"
        resolveOperations[1].details.projectPath == null
        resolveOperations[1].details.buildPath == ":"
        resolveOperations[1].details.scriptConfiguration == true
        resolveOperations[1].details.configurationDescription == null
        resolveOperations[1].details.configurationVisible == true
        resolveOperations[1].details.configurationTransitive == true
        resolveOperations[1].result.resolvedDependenciesCount == 2
    }

    @Unroll
    def "#scriptType script classpath configurations are exposed"() {
        setup:
        def m1 = mavenHttpRepo.module('org.foo', 'root-dep').publish()

        def initScript = file('init.gradle')
        initScript << ''
        executer.usingInitScript(initScript)

        file('scriptPlugin.gradle') << '''
        task foo
        '''

        buildFile << '''
        apply from: 'scriptPlugin.gradle'
        '''

        file(scriptFileName) << """                
            $scriptBlock {
                repositories {
                    maven { url '${mavenHttpRepo.uri}' }
                }
                dependencies {
                    classpath 'org.foo:root-dep:1.0'
                }
            }

        """

        m1.allowAll()
        when:
        run "foo"

        then:
        def resolveOperations = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        resolveOperations.size() == 1
        resolveOperations[0].details.buildPath == ":"
        resolveOperations[0].details.configurationName == "classpath"
        resolveOperations[0].details.projectPath == null
        resolveOperations[0].details.scriptConfiguration == true
        resolveOperations[0].details.configurationDescription == null
        resolveOperations[0].details.configurationVisible == true
        resolveOperations[0].details.configurationTransitive == true
        resolveOperations[0].result.resolvedDependenciesCount == 1

        where:
        scriptType      | scriptBlock   | scriptFileName
        "project build" | 'buildscript' | getDefaultBuildFileName()
        "script plugin" | 'buildscript' | "scriptPlugin.gradle"
        "settings"      | 'buildscript' | 'settings.gradle'
        "init"          | 'initscript'  | 'init.gradle'

    }

    private void setupComposite() {
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

        settingsFile << """
        rootProject.name='root'
        includeBuild 'my-composite-app'
        """
        mavenHttpRepo.module('org.foo', 'app-dep').publish().allowAll()
    }

}
