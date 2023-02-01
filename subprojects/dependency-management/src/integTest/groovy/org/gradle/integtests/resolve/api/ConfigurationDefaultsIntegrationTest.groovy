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
import spock.lang.Issue

class ConfigurationDefaultsIntegrationTest extends AbstractDependencyResolutionTest {
    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        mavenRepo.module("org", "default-dependency").publish()
        mavenRepo.module("org", "explicit-dependency").publish()
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
configurations {
    conf
    child.extendsFrom conf
}
repositories {
    maven { url '${mavenRepo.uri}' }
}

if (System.getProperty('explicitDeps')) {
    dependencies {
        conf "org:explicit-dependency:1.0"
    }
}
"""
    }

    def "can use defaultDependencies to specify default dependencies"() {
        buildFile << """
configurations.conf.defaultDependencies { deps ->
    deps.add project.dependencies.create("org:default-dependency:1.0")
}
"""
        resolve.prepare {
            config("conf", "checkDeps")
            config("child", "checkChild")
        }

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:default-dependency:1.0")
            }
        }

        when:
        run "checkChild"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:default-dependency:1.0")
            }
        }

        when:
        executer.withArgument("-DexplicitDeps=yes")
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:explicit-dependency:1.0")
            }
        }
    }

    @Issue("gradle/gradle#3908")
    def "defaultDependencies action is executed only when configuration participates in resolution"() {
        buildFile << """
configurations {
    other
    conf {
        defaultDependencies { deps ->
            println 'project.status == ' + project.status
            assert project.status == 'foo'
            deps.add project.dependencies.create("org:default-dependency:1.0")
        }
    }
}
dependencies {
    other "org:explicit-dependency:1.0"
}
// Resolve unrelated configuration should not realize defaultDependencies
println configurations.other.files

project.status = 'foo'
"""
        resolve.prepare()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:default-dependency:1.0")
            }
        }
    }

    @Issue("gradle/gradle#812")
    def "can use defaultDependencies in a multi-project build"() {
        buildFile << """
subprojects {
    apply plugin: 'java'

    repositories {
        maven { url '${mavenRepo.uri}' }
    }
}

project(":producer") {
    configurations {
        implementation {
            defaultDependencies {
                add(project.dependencies.create("org:default-dependency:1.0"))
            }
        }
    }
    dependencies {
        if (System.getProperty('explicitDeps')) {
            implementation "org:explicit-dependency:1.0"
        }
    }
}

project(":consumer") {
    dependencies {
        implementation project(":producer")
    }
}
"""
        resolve.prepare("runtimeClasspath")
        resolve.expectDefaultConfiguration("runtimeElements")
        settingsFile << """
include 'consumer', 'producer'
"""

        when:
        executer.withArgument("-DexplicitDeps=yes")
        run ":consumer:checkDeps"

        then:
        resolve.expectGraph {
            root(":consumer", "test:consumer:") {
                project(":producer", "test:producer:") {
                    module("org:explicit-dependency:1.0")
                }
            }
        }

        when:
        run ":consumer:checkDeps"

        then:
        resolve.expectGraph {
            root(":consumer", "test:consumer:") {
                project(":producer", "test:producer:") {
                    module("org:default-dependency:1.0")
                }
            }
        }
    }

    def "can use defaultDependencies in a composite build"() {
        buildTestFixture.withBuildInSubDir()

        def producer = singleProjectBuild("producer") {
            buildFile << """
    apply plugin: 'java'

    repositories {
        maven { url '${mavenRepo.uri}' }
    }
    configurations {
        implementation {
            defaultDependencies {
                add(project.dependencies.create("org:default-dependency:1.0"))
            }
        }
    }
"""
        }

        settingsFile << """
    includeBuild '${producer.toURI()}'
"""
        buildFile << """
    apply plugin: 'java'
    repositories {
        maven { url '${mavenRepo.uri}' }
    }

    repositories {
        maven { url '${mavenRepo.uri}' }
    }
    dependencies {
        implementation 'org.test:producer:1.0'
    }
"""
        resolve.prepare("runtimeClasspath")
        resolve.expectDefaultConfiguration("runtimeElements")

        when:
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:producer:1.0", ":producer", "org.test:producer:1.0") {
                    compositeSubstitute()
                    module("org:default-dependency:1.0")
                }
            }
        }
    }

    def "can use beforeResolve to specify default dependencies"() {
        buildFile << """
configurations.conf.incoming.beforeResolve {
    if (configurations.conf.dependencies.empty) {
        configurations.conf.dependencies.add project.dependencies.create("org:default-dependency:1.0")
    }
}
"""
        resolve.prepare()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:default-dependency:1.0")
            }
        }

        when:
        executer.withArgument("-DexplicitDeps=yes")
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:explicit-dependency:1.0")
            }
        }
    }

    def "fails if beforeResolve used to add dependencies to observed configuration"() {
        resolve.prepare()
        buildFile << """
configurations.conf.incoming.beforeResolve {
    if (configurations.conf.dependencies.empty) {
        configurations.conf.dependencies.add project.dependencies.create("org:default-dependency:1.0")
    }
}
task broken {
    def child = configurations.child
    def conf = configurations.conf
    doLast {
        child.files
        conf.files
    }
}
"""

        when:
        fails "broken"

        then:
        failure.assertHasCause "Cannot change dependencies of dependency configuration ':conf' after it has been included in dependency resolution."
    }

    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "copied configuration has independent set of listeners"() {
        buildFile << """
configurations {
  conf
}

def calls = []

def conf = configurations.conf
conf.incoming.beforeResolve { incoming ->
    calls << "sharedBeforeResolve"
}
conf.withDependencies { incoming ->
    calls << "sharedWithDependencies"
}

def confCopy = conf.copyRecursive()
configurations.add(confCopy)

conf.incoming.beforeResolve { incoming ->
    calls << "confBeforeResolve"
}
conf.withDependencies { incoming ->
    calls << "confWithDependencies"
}
confCopy.incoming.beforeResolve { incoming ->
    calls << "copyBeforeResolve"
}
confCopy.withDependencies { incoming ->
    calls << "copyWithDependencies"
}

task check {
    doLast {
        conf.resolve()
        assert calls == ["sharedWithDependencies", "confWithDependencies", "sharedBeforeResolve", "confBeforeResolve"]
        calls.clear()

        confCopy.resolve()
        assert calls == ["sharedWithDependencies", "copyWithDependencies", "sharedBeforeResolve", "copyBeforeResolve"]
    }
}
"""

        expect:
        succeeds ":check"
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "copied configuration have unique names"() {
        buildFile << """
            configurations {
              conf
            }

            task check {
                doLast {
                    assert configurations.conf.copyRecursive().name == 'confCopy'
                    assert configurations.conf.copyRecursive().name == 'confCopy2'
                    assert configurations.conf.copyRecursive().name == 'confCopy3'
                    assert configurations.conf.copy().name == 'confCopy4'
                    assert configurations.conf.copy().name == 'confCopy5'
                }
            }
            """
        expect:
        succeeds ":check"
    }

    def "configuration getAll is deprecated"() {
        given:
        buildFile << """
            configurations {
                conf {
                    getAll()
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Calling the Configuration.getAll() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use the configurations container to access the set of configurations instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_get_all")
        succeeds "help"
    }
}
