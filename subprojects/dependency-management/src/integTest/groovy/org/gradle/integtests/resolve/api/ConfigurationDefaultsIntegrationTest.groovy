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
    doLast {
        configurations.child.resolve()
        configurations.conf.resolve()
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
        executer.expectDocumentedDeprecationWarning("Copying configurations has been deprecated. This is scheduled to be removed in Gradle 9.0. Consider creating a new configuration and extending configuration ':conf' instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configuration_copying_deprecated")
        succeeds ":check"
    }

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
        executer.expectDocumentedDeprecationWarning("Copying configurations has been deprecated. This is scheduled to be removed in Gradle 9.0. Consider creating a new configuration and extending configuration ':conf' instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configuration_copying_deprecated")
        executer.expectDocumentedDeprecationWarning("Copying configurations has been deprecated. This is scheduled to be removed in Gradle 9.0. Consider creating a new configuration and extending configuration ':conf' instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configuration_copying_deprecated")
        executer.expectDocumentedDeprecationWarning("Copying configurations has been deprecated. This is scheduled to be removed in Gradle 9.0. Consider creating a new configuration and extending configuration ':conf' instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configuration_copying_deprecated")
        executer.expectDocumentedDeprecationWarning("Copying configurations has been deprecated. This is scheduled to be removed in Gradle 9.0. Consider creating a new configuration and extending configuration ':conf' instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configuration_copying_deprecated")
        executer.expectDocumentedDeprecationWarning("Copying configurations has been deprecated. This is scheduled to be removed in Gradle 9.0. Consider creating a new configuration and extending configuration ':conf' instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configuration_copying_deprecated")
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

    def "can extend as an alternative to copying configurations"() {
        buildFile.text = """
            plugins {
                id 'java-library'
            }
            
            def implementationCopy = configurations.implementation.copy()

            configurations {
                implementationExtension {
                    extendsFrom implementation
                    
                    // Deprecation on copy != deprecation on original, so match the copy
                    if (implementationCopy.deprecatedForConsumption) deprecateForConsumption()
                    if (implementationCopy.deprecatedForResolution) deprecateForResolution()
                    if (implementationCopy.deprecatedForDeclarationAgainst) deprecateForDeprecationAgainst()
                    
                    visible = implementation.visible
                    transitive = implementation.transitive
                    description = implementation.description
                    
                    // No API
                    //defaultDependencyActions = implementation.defaultDependencyActions
                    //withDependencyActions = implementation.withDependencyActions
                    //dependencyResolutionListeners = implementation.dependencyResolutionListeners
                    
                    // NO API
                    //declarationAlternatives.addAll(implementation.declarationAlternatives)
                    //resolutionAlternatives.addAll(implementation.resolutionAlternatives)
                    //consumptionDeprecation.addAll(implementation.consumptionDeprecation)
                    
                    artifacts.addAll(implementation.getAllArtifacts())
                    
                    implementation.attributes.keySet().each { attr ->
                        Object value = implementation.attributes.attribute(attribute)
                        attributes.attribute(org.gradle.internal.Cast.uncheckedNonnullCast(attribute), value)
                    }
         
                    implementation.excludeRules.forEach { rule -> excludeRules.add(rule) }
                    implementation.extendsFrom.each { extended ->
                        extended.excludeRules.forEach { rule -> excludeRules.add(rule) }
                    }
                    
                    dependencies.addAll(implementation.dependencies)
                    
                    dependencyConstraints.addAll(implementation.dependencyConstraints)
                }
            }

            task checkCopy {
                doLast {
                    assert configurations.implementationExtension.canBeConsumed == implementationCopy.canBeConsumed
                    assert configurations.implementationExtension.canBeResolved == implementationCopy.canBeResolved
                    assert configurations.implementationExtension.canBeDeclaredAgainst == implementationCopy.canBeDeclaredAgainst
                    assert configurations.implementationExtension.deprecatedForConsumption == implementationCopy.deprecatedForConsumption
                    assert configurations.implementationExtension.deprecatedForResolution == implementationCopy.deprecatedForResolution
                    assert configurations.implementationExtension.deprecatedForDeclarationAgainst == implementationCopy.deprecatedForDeclarationAgainst
                    
                    assert configurations.implementationExtension.visible == implementationCopy.visible
                    assert configurations.implementationExtension.transitive == implementationCopy.transitive
                    assert configurations.implementationExtension.description == implementationCopy.description
                    
                    // No API
                    //assert configurations.implementationExtension.defaultDependencyActions == implementationCopy.defaultDependencyActions
                    //assert configurations.implementationExtension.withDependencyActions == implementationCopy.withDependencyActions
                    //assert configurations.implementationExtension.dependencyResolutionListeners == implementationCopy.dependencyResolutionListeners
                    
                    // No API
                    //assert configurations.implementationExtension.declarationAlternatives == implementationCopy.declarationAlternatives
                    //assert configurations.implementationExtension.resolutionAlternatives == implementationCopy.resolutionAlternatives
                    //assert configurations.implementationExtension.consumptionDeprecation == implementationCopy.consumptionDeprecation
                    
                    assert configurations.implementationExtension.getAllArtifacts() == implementationCopy.getAllArtifacts()
                    
                    assert configurations.implementationExtension.attributes == implementationCopy.attributes
                    
                    assert configurations.implementationExtension.excludeRules == implementationCopy.excludeRules
                    
                    assert configurations.implementationExtension.dependencies == implementationCopy.dependencies
                   
                    assert configurations.implementationExtension.dependencyConstraints == implementationCopy.dependencyConstraints
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Copying configurations has been deprecated. This is scheduled to be removed in Gradle 9.0. Consider creating a new configuration and extending configuration ':implementation' instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configuration_copying_deprecated")
        succeeds ":checkCopy"
    }
}
