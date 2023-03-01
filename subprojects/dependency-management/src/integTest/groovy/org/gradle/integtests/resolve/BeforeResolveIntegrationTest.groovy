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

import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

class BeforeResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def "can use beforeResolve hook to modify add dependencies"() {
        mavenRepo.module('org.test', 'dep1', '1.0').publish()
        mavenRepo.module('org.test', 'dep2', '1.0').publish()
        mavenRepo.module('org.test', 'dep3', '1.0').publish()

        buildFile << """
repositories {
    maven { url '${mavenRepo.uri}' }
}
configurations {
    conf
}
dependencies {
    conf 'org.test:dep1:1.0'
}

configurations.conf.incoming.beforeResolve { resolvableDependencies ->
    project.dependencies.add('conf', 'org.test:dep2:1.0')
}

task printFiles {
    def conf = configurations.conf
    doLast {
        def files = conf.collect { it.name }
        println files
        assert files == ['dep1-1.0.jar', 'dep2-1.0.jar']
    }
}

task printFilesWithConfigurationInput {
    dependsOn configurations.conf
    def conf = configurations.conf
    doLast {
        def files = conf.collect { it.name }
        println files
        assert files == ['dep1-1.0.jar', 'dep2-1.0.jar']
    }
}

task copyFiles(type:Copy) {
    from configurations.conf
    into 'libs'
}
"""

        when:
        succeeds 'printFiles'

        then:
        outputContains('[dep1-1.0.jar, dep2-1.0.jar]')

        when:
        succeeds 'printFilesWithConfigurationInput'

        then:
        outputContains('[dep1-1.0.jar, dep2-1.0.jar]')

        when:
        succeeds 'copyFiles'
        then:
        file('libs').assertHasDescendants('dep1-1.0.jar', 'dep2-1.0.jar')

        when:
        buildFile << """
// add another dependency to conf
configurations.conf.incoming.beforeResolve { resolvableDependencies ->
    project.dependencies.add('conf', 'org.test:dep3:1.0')
}
"""
        succeeds "copyFiles"
        then:
        file('libs').assertHasDescendants('dep1-1.0.jar', 'dep2-1.0.jar', 'dep3-1.0.jar')
    }

    @Issue("gradle/gradle#2480")
    def "can use beforeResolve hook to modify dependency excludes"() {
        def module1 = mavenRepo.module('org.test', 'excluded-dep', '1.0').publish()
        mavenRepo.module('org.test', 'direct-dep', '1.0').dependsOn(module1).publish()

        buildFile << """
repositories {
    maven { url '${mavenRepo.uri}' }
}
configurations {
    conf
}
dependencies {
    conf 'org.test:direct-dep:1.0'
}

configurations.conf.incoming.beforeResolve { resolvableDependencies ->
    resolvableDependencies.dependencies.each { dependency ->
        dependency.exclude module: 'excluded-dep'
    }
}

task printFiles {
    def conf = configurations.conf
    doLast {
        def files = conf.collect { it.name }
        println files
        assert files == ['direct-dep-1.0.jar']
    }
}

task printFilesWithConfigurationInput {
    dependsOn configurations.conf
    def conf = configurations.conf
    doLast {
        def files = conf.collect { it.name }
        println files
        assert files == ['direct-dep-1.0.jar']
    }
}

task copyFiles(type:Copy) {
    from configurations.conf
    into 'libs'
}
"""

        when:
        succeeds 'printFiles'

        then: // Succeeds: configuration is not 'resolved' as part of the task inputs
        outputContains('[direct-dep-1.0.jar]')

        when:
        succeeds 'printFilesWithConfigurationInput'

        and:
        succeeds 'copyFiles'

        then: // Currently fails: excluded dependency is copied as part of configuration
        file('libs').assertHasDescendants('direct-dep-1.0.jar')
    }

    // This emulates the behaviour of the Spring Dependency Management plugin when applying dependency excludes from a BOM
    def "can use beforeResolve hook to modify dependency excludes for configuration hierarchy"() {
        mavenRepo.module('org.test', 'module1', '1.0').publish()
        mavenRepo.module('org.test', 'module2', '1.0').publish()

        given:
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            dependencies {
                implementation('org.test:module1:1.0')
                testImplementation('org.test:module2:1.0')
            }

            configurations.all { configuration ->
                configuration.incoming.beforeResolve { resolvableDependencies ->
                    resolvableDependencies.dependencies.each { dependency ->
                        dependency.exclude module: 'excluded-dep'
                    }
                }
            }

            task resolveDependencies {
                def compile = configurations.compileClasspath
                def testCompile = configurations.testCompileClasspath
                doLast {
                    compile.files
                    testCompile.files
                }
            }
"""

        expect:
        succeeds 'resolveDependencies'
    }

    @Requires(TestPrecondition.ONLINE)
    // This emulates the behaviour of the Spring Dependency Management plugin when applying dependency excludes from a BOM
    def "can use beforeResolve hook to modify excludes for a dependency shared with an already-resolved configuration"() {
        given: "3 modules, where there are dependency relations such that module1 depends on module2 and module2 depends on module3"
        mavenRepo.module('org.test', 'module1', '1.0').publish()
        mavenRepo.module('org.test', 'module2', '1.0')
            .dependsOn('org.test', 'module1', '1.0')
            .publish()
        mavenRepo.module('org.test', 'module3', '1.0')
            .dependsOn('org.test', 'module2', '1.0')
            .publish()

        and: "a build where conf a and b extend shared, which deps module3, and where if module3 is going to be resolved for conf b, then module 1 is excluded via a beforeResolve hook"
        buildFile << """
configurations {
   shared
   a.extendsFrom shared
   b.extendsFrom shared
}

repositories {
    maven { url '${mavenRepo.uri}' }
}

configurations.b.incoming.beforeResolve { resolvableDependencies ->
    resolvableDependencies.dependencies.each { dependency ->
        if (dependency.name == 'module3') {
            dependency.exclude module: 'module1'
        }
    }
}

dependencies {
    shared 'org.test:module3:1.0'
}

task resolveDependencies {
    def rootA = configurations.a.incoming.resolutionResult.rootComponent
    def rootB = configurations.b.incoming.resolutionResult.rootComponent
    doLast {
        rootA.get()
        rootB.get()
        rootA.get()
    }
}
"""

        expect: "that resolving conf a, then b, then a again, succeeds"
        succeeds 'resolveDependencies'
    }

    def "can modify a configuration in a beforeResolve hook when the hook resolves another configuration"() {
        mavenRepo.module('org.test', 'module1', '1.0').publish()
        mavenRepo.module('org.test', 'module2', '1.0').publish()
        settingsFile << """
           include ":lib"
        """
        buildFile << """
            allprojects {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
            configurations {
                foo
                bar {
                    incoming.beforeResolve {
                        println "resolving foo..."
                        foo.resolve()
                        // bar should still be in an unresolved state, so we should be able to modify the
                        // things like dependency constraints here
                        bar.validateMutation(${MutationValidator.MutationType.class.name}.DEPENDENCIES)
                    }
                }
            }
            dependencies {
                foo project(path: ':lib', configuration: 'foo')
                bar "org.test:module2:1.0"
            }
            task a {
                def files = configurations.bar
                inputs.files files
                doLast {
                    files.each { println it }
                }
            }
            task b {
                def files = configurations.bar
                inputs.files files
                doLast {
                    files.each { println it }
                }
            }
        """
        file('lib/build.gradle') << """
            configurations {
                foo
            }

            dependencies {
                foo "org.test:module1:1.0"
            }
        """

        expect:
        executer.withArgument("--parallel")
        succeeds "a", "b"

        and:
        output.count("resolving foo") == 1
    }
}
