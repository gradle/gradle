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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class MavenVersionRangeResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        new ResolveTestFixture(buildFile).addDefaultVariantDerivationStrategy()
    }

    @Issue("GRADLE-3334")
    def "can resolve version range with single value specified"() {
        given:
        settingsFile << "rootProject.name = 'test' "
        buildFile << """
repositories {
    maven {
        url "${mavenRepo.uri}"
    }
}

configurations { compile }

dependencies {
    compile group: "org.test", name: "projectA", version: "[1.1]"
}
"""
        and:
        mavenRepo.module('org.test', 'projectB', '2.0').publish()
        mavenRepo.module('org.test', 'projectA', '1.1').dependsOn('org.test', 'projectB', '[2.0]').publish()

        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()
        resolve.expectDefaultConfiguration("runtime")

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.1]", "org.test:projectA:1.1") {
                    edge("org.test:projectB:[2.0]", "org.test:projectB:2.0") // Transitive version range is lost when converting to Ivy ModuleDescriptor
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/1898")
    def "can resolve parent pom with version range"() {
        given:
        settingsFile << "rootProject.name = 'test' "
        buildFile << """
repositories {
    maven {
        url "${mavenRepo.uri}"
    }
}

configurations { compile }

dependencies {
    compile group: "org.test", name: "child", version: "1.0"
}
"""
        and:
        mavenRepo.module('org.test', 'child', '1.0').parent('org.test', 'parent', '[2.0,3.0)').publish()
        mavenRepo.module('org.test', 'parent', '1.0').dependsOn('org.test', 'dep', '1.0').publishPom()
        mavenRepo.module('org.test', 'parent', '2.0').dependsOn('org.test', 'dep', '2.0').publishPom()
        mavenRepo.module('org.test', 'parent', '2.1').dependsOn('org.test', 'dep', '2.1').publishPom()
        mavenRepo.module('org.test', 'parent', '3.0').dependsOn('org.test', 'dep', '3.0').publishPom()
        mavenRepo.module('org.test', 'dep', '2.1').publish()

        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectDefaultConfiguration("runtime")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:child:1.0", "org.test:child:1.0") {
                    edge("org.test:dep:2.1", "org.test:dep:2.1")
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/1898")
    def "can resolve pom importing version range"() {
        given:
        settingsFile << "rootProject.name = 'test' "
        buildFile << """
repositories {
    maven {
        url "${mavenRepo.uri}"
    }
}

configurations { compile }

dependencies {
    compile group: "org.test", name: "child", version: "1.0"
}
"""
        and:
        mavenRepo.module('org.test', 'child', '1.0').dependsOn('org.test', 'imported', '[2.0,3.0)', 'pom', 'import').publish()
        mavenRepo.module('org.test', 'imported', '1.0').dependsOn('org.test', 'dep', '2.0').publishPom()
        mavenRepo.module('org.test', 'imported', '2.0').dependsOn('org.test', 'dep', '2.0').publishPom()
        mavenRepo.module('org.test', 'imported', '2.1').dependsOn('org.test', 'dep', '2.1').publishPom()
        mavenRepo.module('org.test', 'imported', '3.0').dependsOn('org.test', 'dep', '3.0').publishPom()
        mavenRepo.module('org.test', 'dep', '2.1').publish()

        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()
        resolve.expectDefaultConfiguration("runtime")

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:child:1.0", "org.test:child:1.0") {
                    edge("org.test:imported:[2.0,3.0)", "org.test:imported:2.1") {
                        artifact(group: 'org.test', module: 'imported', version: '2.1', type: 'pom').byReason("didn't match version 3.0")
                        edge("org.test:dep:2.1", "org.test:dep:2.1")
                    }
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/1898")
    def "error when parent pom with specified version range cannot be found"() {
        given:
        settingsFile << "rootProject.name = 'test' "
        buildFile << """
repositories {
    maven {
        url "${mavenRepo.uri}"
    }
}

configurations { compile }

dependencies {
    compile group: "org.test", name: "child", version: "1.0"
}
"""
        and:
        mavenRepo.module('org.test', 'child', '1.0').parent('org.test', 'parent', '[2.0,3.0)').publish()
        mavenRepo.module('org.test', 'parent', '1.0').dependsOn('org.test', 'dep', '2.0').publishPom()
        mavenRepo.module('org.test', 'parent', '3.0').dependsOn('org.test', 'dep', '3.0').publishPom()

        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause('Could not find any version that matches org.test:parent:[2.0,3.0)')
    }
}
