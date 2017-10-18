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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class ConfigurationMutationIntegrationTest extends AbstractDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        resolve = new ResolveTestFixture(buildFile)

        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
group "org.test"
version "1.1"

configurations {
    conf
    compile.extendsFrom conf
}
repositories {
    maven { url '${mavenRepo.uri}' }
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
    foo.version = '1.0'

    def bar = deps.find { it.name == 'bar' }
    assert bar.version == '2.2'
    bar.version = null
}
configurations.compile.withDependencies { deps ->
    def bar = deps.find { it.name == 'bar' }
    assert bar.version == null
    bar.version = '1.0'
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
        failure.assertHasCause("Cannot change dependencies of configuration ':compile' after it has been resolved.")

        and:
        fails "mutateParent"
        failure.assertHasCause("Cannot change dependencies of configuration ':conf' after it has been included in dependency resolution.")
    }

    void resolvedGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.prepare()
        succeeds ":checkDeps"

        resolve.expectGraph {
            root(":", "org.test:root:1.1", closure)
        }
    }

}
