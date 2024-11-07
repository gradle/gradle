/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class ConfigurationMutationIntegrationTest extends AbstractDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        resolve = new ResolveTestFixture(buildFile, "compile").expectDefaultConfiguration("runtime")
        resolve.addDefaultVariantDerivationStrategy()

        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
group = "org.test"
version = "1.1"

configurations {
    conf
    compile.extendsFrom conf
}
repositories {
    maven { url = '${mavenRepo.uri}' }
}

"""
    }

    def "can use withDependencies to mutate dependencies of parent configuration"() {
        when:
        buildFile << """
dependencies {
    conf "org:to-remove:1.0"
    conf "org:foo:1.0"
}
configurations.conf.withDependencies { deps ->
    deps.remove deps.find { it.name == 'to-remove' }
    deps.add project.dependencies.create("org:bar:1.0")
}
configurations.compile.withDependencies { deps ->
    assert deps.empty : "Compile dependencies should be empty"
}
"""

        then:
        resolvedGraph {
            module("org:foo:1.0")
            module("org:bar:1.0")
        }
    }

    def "can use withDependencies to mutate declared dependencies"() {
        when:
        buildFile << """
dependencies {
    compile "org:to-remove:1.0"
    compile "org:foo:1.0"
}
configurations.compile.withDependencies { deps ->
    deps.remove deps.find { it.name == 'to-remove' }
    deps.add project.dependencies.create("org:bar:1.0")
}
configurations.conf.withDependencies { deps ->
    assert deps.empty : "Parent dependencies should be empty"
}
"""

        then:
        resolvedGraph {
            module("org:foo:1.0")
            module("org:bar:1.0")
        }
    }

    def "withDependencies actions are executed in order added"() {
        when:
        buildFile << """
dependencies {
    compile "org:foo:1.0"
}
configurations.compile.withDependencies { DependencySet deps ->
    assert deps.collect {it.name} == ['foo']
}
configurations.compile.withDependencies { DependencySet deps ->
    deps.add project.dependencies.create("org:bar:1.0")
}
configurations.compile.withDependencies { DependencySet deps ->
    assert deps.collect {it.name} == ['foo', 'bar']
}
"""

        then:
        resolvedGraph {
            module("org:foo:1.0")
            module("org:bar:1.0")
        }
    }

    def "withDependencies action can mutate dependencies provided by defaultDependencies"() {
        when:
        buildFile << """
configurations.compile.withDependencies { DependencySet deps ->
    assert deps.collect {it.name} == ['foo']
    deps.add project.dependencies.create("org:bar:1.0")
}
configurations.compile.defaultDependencies { DependencySet deps ->
    deps.add project.dependencies.create("org:foo:1.0")
}
"""

        then:
        resolvedGraph {
            module("org:foo:1.0")
            module("org:bar:1.0")
        }
    }

    def "can use withDependencies to mutate dependency versions"() {
        when:
        buildFile << """
dependencies {
    compile "org:foo"
    compile "org:bar:2.2"
}
configurations.compile.withDependencies { deps ->
    def foo = deps.find { it.name == 'foo' }
    assert foo.version == null
    foo.version { require '1.0' }

    def bar = deps.find { it.name == 'bar' }
    assert bar.version == '2.2'
    bar.version { require null }
}
configurations.compile.withDependencies { deps ->
    def bar = deps.find { it.name == 'bar' }
    assert bar.version == null
    bar.version { require '1.0' }
}
"""

        then:
        resolvedGraph {
            module("org:foo:1.0")
            module("org:bar:1.0")
        }
    }

    def "provides useful error message when withDependencies action fails to execute"() {
        when:
        buildFile << """
configurations.compile.withDependencies {
    throw new RuntimeException("Bad user code")
}
"""

        then:
        resolve.prepare()
        fails ":checkDeps"

        failure.assertHasCause("Bad user code")
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "cannot add withDependencies rule after configuration has been used"() {
        when:
        buildFile << """
            dependencies {
                compile "org:foo:1.0"
            }
            task mutateResolved(type:Copy) {
                from configurations.compile
                into "output"
                doLast {
                    configurations.compile.withDependencies {
                        println "Late added"
                    }
                }
            }
            task mutateParent(type:Copy) {
                from configurations.compile
                into "output"
                doLast {
                    configurations.conf.withDependencies {
                        println "Late added"
                    }
                }
            }
"""

        then:
        fails "mutateResolved"
        failure.assertHasCause("Cannot change dependencies of dependency configuration ':compile' after it has been resolved.")

        and:
        fails "mutateParent"
        failure.assertHasCause("Cannot change dependencies of dependency configuration ':conf' after it has been included in dependency resolution.")
    }

    void resolvedGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.prepare()
        succeeds ":checkDeps"

        resolve.expectGraph {
            root(":", "org.test:root:1.1", closure)
        }
    }

    def "can use withDependencies to alter configuration resolved in a multi-project build"() {
        mavenRepo.module("org", "explicit-dependency", "3.4").publish()
        mavenRepo.module("org", "added-dependency", "3.4").publish()
        buildFile.text = """
subprojects {
    apply plugin: 'java'

    repositories {
        maven { url = '${mavenRepo.uri}' }
    }
}

project(":producer") {
    configurations {
        implementation {
            withDependencies { deps ->
                deps.each {
                    it.version {
                       require '3.4'
                    }
                }
                deps.add(project.dependencies.create("org:added-dependency:3.4"))
            }
        }
    }
    dependencies {
        implementation "org:explicit-dependency"
    }
}

project(":consumer") {
    dependencies {
        implementation project(":producer")
    }
}
"""
        resolve.prepare("runtimeClasspath")
        createDirs("consumer", "producer")
        settingsFile << """
include 'consumer', 'producer'
"""
        expect:
        // relying on default dependency
        succeeds(":consumer:checkDeps")
        resolve.expectGraph {
            root(":consumer", "root:consumer:") {
                project(":producer", "root:producer:") {
                    module("org:explicit-dependency:3.4")
                    module("org:added-dependency:3.4")
                }
            }
        }
    }

    def "can use defaultDependencies in a composite build"() {
        buildTestFixture.withBuildInSubDir()
        mavenRepo.module("org", "explicit-dependency", "3.4").publish()
        mavenRepo.module("org", "added-dependency", "3.4").publish()

        def producer = singleProjectBuild("producer") {
            buildFile << """
    apply plugin: 'java'

    repositories {
        maven { url = '${mavenRepo.uri}' }
    }
    configurations {
        implementation {
            withDependencies { deps ->
                deps.each {
                    it.version {
                        require '3.4'
                    }
                }
                deps.add(project.dependencies.create("org:added-dependency:3.4"))
            }
        }
    }
    dependencies {
        implementation "org:explicit-dependency"
    }
"""
        }

        settingsFile << """
    includeBuild '${producer.toURI()}'
"""
        buildFile << """
    apply plugin: 'java'
    repositories {
        maven { url = '${mavenRepo.uri}' }
    }

    repositories {
        maven { url = '${mavenRepo.uri}' }
    }
    dependencies {
        implementation 'org.test:producer:1.0'
    }
"""
        resolve.prepare("runtimeClasspath")

        expect:
        succeeds ":checkDeps"
        resolve.expectGraph {
            root(":", "org.test:root:1.1") {
                edge("org.test:producer:1.0", ":producer", "org.test:producer:1.0") {
                    compositeSubstitute()
                    module("org:explicit-dependency:3.4")
                    module("org:added-dependency:3.4")
                }
            }
        }
    }

    def "can lazily add dependencies to a configuration"() {
        given:
        buildFile.text = """
            repositories {
                maven { url = '${mavenRepo.uri}' }
            }

            configurations {
                dependencyScope("conf")
                resolvable("res") {
                    extendsFrom conf
                }
            }

            configurations.conf.dependencies.addLater(provider(() -> project.dependencies.create("org:foo:1.0")))

            task resolve {
                def files = configurations.res.incoming.files
                doLast {
                    assert files*.name == ["foo-1.0.jar"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "can lazily add dependency constraints to a configuration"() {
        mavenRepo.module("org", "foo", "2.0").publish()
        given:
        buildFile.text = """
            repositories {
                maven { url = '${mavenRepo.uri}' }
            }

            configurations {
                dependencyScope("conf")
                resolvable("res") {
                    extendsFrom conf
                }
            }

            dependencies {
                conf "org:foo:1.0"
            }

            configurations.conf.dependencyConstraints.addLater(provider(() -> project.dependencies.constraints.create("org:foo:2.0")))

            task resolve {
                def files = configurations.res.incoming.files
                doLast {
                    assert files*.name == ["foo-2.0.jar"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "modifying dependency and constraint attributes are deprecated after resolution"() {
        given:
        ["1.0", "2.0"].each { version ->
            mavenRepo.module("org", "foo", version).publish()
            mavenRepo.module("org", "bar", version).publish()
            mavenRepo.module("org", "baz", version).publish()
            mavenRepo.module("org", "qux", version).publish()
        }

        buildFile << """
            ${mavenTestRepository()}

            configurations {
                dependencyScope("parent")
                dependencyScope("deps") {
                    extendsFrom(parent)
                }
                resolvable("res") {
                    extendsFrom(deps)
                    incoming.beforeResolve {
                        dependencies.each { dep ->
                            dep.version {
                                require "2.0"
                            }
                        }
                        dependencyConstraints.each { dep ->
                            dep.version {
                                require "2.0"
                            }
                        }
                    }
                }
            }

            dependencies {
                parent "org:foo:1.0"
                deps "org:bar:1.0"
                constraints {
                    parent "org:baz:1.0"
                    deps "org:qux:1.0"
                }
            }

            task resolve {
                def files = configurations.res
                dependsOn(files) // Ensures we calculate build dependenices, and thus perform a resolution before beforeResolve.
                doLast {
                    println files.files
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Mutating the dependency attributes of configuration ':deps' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
        executer.expectDocumentedDeprecationWarning("Mutating the dependency attributes of configuration ':parent' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
        executer.expectDocumentedDeprecationWarning("Mutating the dependency constraint attributes of configuration ':deps' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
        executer.expectDocumentedDeprecationWarning("Mutating the dependency constraint attributes of configuration ':parent' after it has been resolved or consumed. This behavior has been deprecated. This will fail with an error in Gradle 9.0. After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#mutate_configuration_after_locking")
        succeeds("resolve")
    }
}
