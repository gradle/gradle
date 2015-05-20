/*
 * Copyright 2015 the original author or authors.
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

public class ConfigurationDefaultsIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()

        buildFile << """
configurations {
    conf
    child.extendsFrom conf
}
repositories {
    maven { url '${mavenRepo.uri}' }
}

if (project.hasProperty('explicitDeps')) {
    dependencies {
        conf "org:bar:1.0"
    }
}
task checkDefault << {
    if (project.hasProperty('resolveChild')) {
        configurations.child.resolve()
    }

    def deps = configurations.conf.incoming.resolutionResult.allDependencies
    assert deps*.selected.id.displayName == ['org:foo:1.0']

    def files = configurations.conf.files
    assert files*.name == ["foo-1.0.jar"]
}
task checkExplicit << {
    def deps = configurations.conf.incoming.resolutionResult.allDependencies
    assert deps*.selected.id.displayName == ['org:bar:1.0']

    def files = configurations.conf.files
    assert files*.name == ["bar-1.0.jar"]
}
"""
    }

    def "can use defaultDependencies to specify default dependencies"() {
        buildFile << """
def fooDep = project.dependencies.create("org:foo:1.0")
configurations.conf.defaultDependencies { deps ->
    deps.add(fooDep)
}
"""

        expect:
        succeeds "checkDefault"

        when:
        executer.withArgument("-PresolveChild")

        then:
        succeeds "checkDefault"

        when:
        executer.withArgument("-PexplicitDeps")

        then:
        succeeds "checkExplicit"
    }

    def "can use beforeResolve to specify default dependencies"() {
        buildFile << """
def fooDep = project.dependencies.create("org:foo:1.0")
configurations.conf.incoming.beforeResolve {
    if (configurations.conf.dependencies.empty) {
        configurations.conf.dependencies.add(fooDep)
    }
}
"""

        expect:
        succeeds "checkDefault"

        when:
        executer.withArgument("-PexplicitDeps")

        then:
        succeeds "checkExplicit"
    }

    def "deprecation warning informs about defaultDependencies if beforeResolve used to add dependencies to observed configuration"() {
        buildFile << """
def fooDep = project.dependencies.create("org:foo:1.0")
configurations.conf.incoming.beforeResolve {
    if (configurations.conf.dependencies.empty) {
        configurations.conf.dependencies.add(fooDep)
    }
}
"""


        when:
        executer.withArgument("-PresolveChild")
        executer.withDeprecationChecksDisabled()

        then:
        succeeds "checkDefault"

        and:
        output.contains "Changed dependencies of configuration ':conf' after it has been included in dependency resolution."
        output.contains "Use 'defaultDependencies' instead of 'beforeResolve' to specify default dependencies for a configuration."
        output.contains "Changed dependencies of parent of configuration ':child' after it has been resolved."
    }
}
