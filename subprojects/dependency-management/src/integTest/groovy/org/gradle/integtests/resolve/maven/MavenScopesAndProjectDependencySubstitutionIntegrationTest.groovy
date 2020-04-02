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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class MavenScopesAndProjectDependencySubstitutionIntegrationTest extends AbstractDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        resolve.expectDefaultConfiguration("runtime")
        settingsFile << """
            rootProject.name = 'testproject'
            include 'child1', 'child2'
        """
        buildFile << """
            allprojects {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                    ivy { url '${ivyRepo.uri}' }
                }
            }
            project(':child1') {
                configurations {
                    conf
                }
            }
        """
    }

    def "when no target configuration is specified then a dependency on maven module includes the default configuration of target project when they are present"() {
        mavenRepo.module("org.test", "m1", "1.0").publish()
        mavenRepo.module("org.test", "m2", "1.0").publish()
        mavenRepo.module("org.test", "m3", "1.0").publish()
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn("org.test", "replaced", "1.0")
            .publish()
        mavenRepo.module("org.test", "dont-ignore-me", "1.0").publish()

        buildFile << """
project(':child1') {
    dependencies {
        conf 'org.test:maven:1.0'
    }
    configurations.conf.resolutionStrategy.dependencySubstitution {
        substitute module('org.test:replaced:1.0') with project(':child2')
    }
}
project(':child2') {
    configurations {
        compile
        runtime
        master
        other
        create("default")
    }
    dependencies {
        compile 'org.test:m1:1.0'
        runtime 'org.test:m2:1.0'
        master 'org.test:m3:1.0'
        other 'org.test.ignore-me:1.0'
        "default" 'org.test:dont-ignore-me:1.0'
    }
}
"""
        expect:
        succeeds 'child1:checkDep'
        resolve.expectGraph {
            root(':child1', 'testproject:child1:') {
                module('org.test:maven:1.0') {
                    edge('org.test:replaced:1.0', 'project :child2', 'testproject:child2:') {
                        noArtifacts()
                        selectedByRule()
                        module('org.test:dont-ignore-me:1.0')
                    }
                }
            }
        }
    }

    @ToBeFixedForInstantExecution(because = "broken file collection")
    def "when no target configuration is specified then a dependency on maven module includes the runtime dependencies of target project that is using the Java plugin"() {
        mavenRepo.module("org.test", "m1", "1.0").publish()
        mavenRepo.module("org.test", "m2", "1.0").publish()
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn("org.test", "replaced", "1.0")
            .publish()

        buildFile << """
project(':child1') {
    dependencies {
        conf 'org.test:maven:1.0'
    }
    configurations.conf.resolutionStrategy.dependencySubstitution {
        substitute module('org.test:replaced:1.0') with project(':child2')
    }
}
project(':child2') {
    apply plugin: 'java'
    dependencies {
        implementation 'org.test:m1:1.0'
        runtimeOnly 'org.test:m2:1.0'
        compileOnly 'org.test.ignore-me:1.0'
        testImplementation 'org.test.ignore-me:1.0'
        testRuntimeOnly 'org.test.ignore-me:1.0'
    }
}
"""
        expect:
        succeeds 'child1:checkDep'
        resolve.expectGraph {
            root(':child1', 'testproject:child1:') {
                module('org.test:maven:1.0') {
                    edge('org.test:replaced:1.0', 'project :child2', 'testproject:child2:') {
                        selectedByRule()
                        module('org.test:m1:1.0')
                        module('org.test:m2:1.0')
                    }
                }
            }
        }
    }

    def "a dependency on compile scope of maven module includes the default of target project when they are present"() {
        mavenRepo.module("org.test", "m1", "1.0").publish()
        mavenRepo.module("org.test", "m2", "1.0").publish()
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn("org.test", "replaced", "1.0")
            .publish()
        mavenRepo.module("org.test", "dont-ignore-me", "1.0").publish()

        buildFile << """
project(':child1') {
    dependencies {
        conf group: 'org.test', name: 'maven', version: '1.0', configuration: 'compile'
    }
    configurations.conf.resolutionStrategy.dependencySubstitution {
        substitute module('org.test:replaced:1.0') with project(':child2')
    }
}
project(':child2') {
    configurations {
        compile
        runtime
        master
        other
        create("default")
    }
    dependencies {
        compile 'org.test:m1:1.0'
        master 'org.test:m2:1.0'
        runtime 'org.test.ignore-me:1.0'
        other 'org.test.ignore-me:1.0'
        "default" 'org.test:dont-ignore-me:1.0'
    }
}
"""
        expect:
        succeeds 'child1:checkDep'
        resolve.expectGraph {
            root(':child1', 'testproject:child1:') {
                module('org.test:maven:1.0') {
                    configuration = 'compile'
                    edge('org.test:replaced:1.0', 'project :child2', 'testproject:child2:') {
                        selectedByRule()
                        noArtifacts()
                        module('org.test:dont-ignore-me:1.0')
                    }
                }
            }
        }
    }

    @ToBeFixedForInstantExecution(because = "broken file collection")
    def "a dependency on compile scope of maven module includes the runtime dependencies of target project that is using the Java plugin"() {
        mavenRepo.module("org.test", "m1", "1.0").publish()
        mavenRepo.module("org.test", "m2", "1.0").publish()
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn("org.test", "replaced", "1.0")
            .publish()

        buildFile << """
project(':child1') {
    dependencies {
        conf group: 'org.test', name: 'maven', version: '1.0', configuration: 'compile'
    }
    configurations.conf.resolutionStrategy.dependencySubstitution {
        substitute module('org.test:replaced:1.0') with project(':child2')
    }
}
project(':child2') {
    apply plugin: 'java'
    dependencies {
        implementation 'org.test:m1:1.0'
        runtimeOnly 'org.test:m2:1.0'

        compileOnly 'org.test:ignore-me:1.0'
        testImplementation 'org.test:ignore-me:1.0'
        testRuntimeOnly 'org.test:ignore-me:1.0'
    }
}
"""
        expect:
        succeeds 'child1:checkDep'
        resolve.expectGraph {
            root(':child1', 'testproject:child1:') {
                module('org.test:maven:1.0') {
                    configuration = 'compile'
                    edge('org.test:replaced:1.0', 'project :child2', 'testproject:child2:') {
                        selectedByRule()
                        module('org.test:m1:1.0')
                        module('org.test:m2:1.0')
                    }
                }
            }
        }
    }
}
