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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.maven.MavenModule
import spock.lang.Issue

class MavenBomResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile, "compile").expectDefaultConfiguration('runtime')
    MavenModule bom
    MavenModule moduleA

    def setup() {
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        settingsFile << "rootProject.name = 'testproject'"
        buildFile << """
            repositories { maven { url = "${mavenHttpRepo.uri}" } }
            configurations { compile }
        """
        moduleA = mavenHttpRepo.module('group', 'moduleA', '2.0').allowAll().publish()
        bom = mavenHttpRepo.module('group', 'bom', '1.0')
            .hasType("pom")
            .allowAll()
    }

    MavenModule bomDependency(String artifact, List<Map<String, String>> exclusions = null) {
        bom.dependencyConstraint(mavenHttpRepo.module('group', artifact, '2.0'), exclusions: exclusions)
    }

    def "can use a bom to select a version"() {
        given:
        bomDependency('moduleA').publish()
        buildFile << """
            dependencies {
                compile "group:moduleA"
                compile platform("group:bom:1.0")
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0:platform-runtime") {
                    constraint("group:moduleA:2.0", "group:moduleA:2.0")
                    noArtifacts()
                }
                edge("group:moduleA", "group:moduleA:2.0") {
                    byConstraint()
                }
            }
        }
    }

    def "a bom dependencyManagement entry can declare excludes which are applied unconditionally to module"() {
        given:
        moduleA.dependsOn(mavenHttpRepo.module("group", "moduleC", "1.0").allowAll().publish()).publish()
        bomDependency('moduleA', [[group: 'group', module: 'moduleC']])
        bomDependency('moduleB', [[group: 'group', module: 'moduleC']]).publish()

        buildFile << """
            dependencies {
                compile "group:moduleA"
                compile platform("group:bom:1.0")
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0:platform-runtime") {
                    constraint("group:moduleA:2.0", "group:moduleA:2.0")
                    noArtifacts()
                }
                edge("group:moduleA", "group:moduleA:2.0") {
                    byConstraint()
                }
            }
        }
    }

    def "exclusions from multiple bom dependencyManagement entries are additive"() {
        given:
        moduleA
            .dependsOn(mavenHttpRepo.module("group", "moduleC", "1.0").allowAll().publish())
            .dependsOn(mavenHttpRepo.module("group", "moduleD", "1.0").allowAll().publish())
            .publish()

        bom.dependencyConstraint(moduleA, exclusions: [[group: 'group', module: 'moduleC']]).publish()

        def bom2 = mavenHttpRepo.module('group', 'bom2', '1.0').hasType("pom").allowAll()
        bom2.dependencyConstraint(moduleA, exclusions: [[group: 'group', module: 'moduleD']]).publish()


        buildFile << """
            dependencies {
                compile "group:moduleA"
                compile platform("group:bom:1.0")
                compile platform("group:bom2:1.0")
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0:platform-runtime") {
                    constraint("group:moduleA:2.0", "group:moduleA:2.0")
                    noArtifacts()
                }
                module("group:bom2:1.0:platform-runtime") {
                    constraint("group:moduleA:2.0", "group:moduleA:2.0")
                    noArtifacts()
                }
                edge("group:moduleA", "group:moduleA:2.0") {
                    byConstraint()
                }
            }
        }
    }

    @Issue("gradle/gradle#8420")
    def "can depend on both platform and library if a published POM represents both of them"() {
        given:
        mavenHttpRepo.module('group', 'moduleC', '1.0').allowAll().publish()
        bomDependency('moduleA')
        bomDependency('moduleB')
        bom.publish()
        bom.pomFile.text = bom.pomFile.text.replace("</project>", '''
                <dependencies>
                    <dependency>
                        <groupId>group</groupId>
                        <artifactId>moduleC</artifactId>
                        <version>1.0</version>
                    </dependency>
                </dependencies>
            </project>''')

        buildFile << """
            dependencies {
                compile platform("group:bom:1.0") // dependency on the platform
                compile "group:bom:1.0" // dependency on library
                compile "group:moduleA"
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0") {
                    variant("platform-runtime", ['org.gradle.category': 'platform', 'org.gradle.status': 'release', 'org.gradle.usage': 'java-runtime'])
                    constraint("group:moduleA:2.0")
                    noArtifacts()
                }
                module("group:bom:1.0") {
                    variant("runtime", ['org.gradle.category': 'library', 'org.gradle.status': 'release', 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar'])
                    module("group:moduleC:1.0")
                    noArtifacts()
                }
                edge("group:moduleA", "group:moduleA:2.0") {
                    byConstraint()
                }
            }
        }
    }

    def "a parent pom is not a bom"() {
        mavenHttpRepo.module('group', 'main', '5.0').allowAll().parent(bom.group, bom.artifactId, bom.version).publish()
        bomDependency('moduleA')
        bomDependency('moduleB')
        bom.publish()

        buildFile << """
            dependencies {
                compile "group:moduleA"
                compile "group:main:5.0"
            }
        """

        when:
        fails 'checkDep'

        then:
        failure.assertHasCause "Could not find group:moduleA:."
    }

    def "a parent pom with dependency entries without versions does not fail the build"() {
        given:
        mavenHttpRepo.module('group', 'main', '5.0').allowAll().parent(bom.group, bom.artifactId, bom.version).publish()
        bomDependency('moduleA')
        bomDependency('moduleB')
        bom.publish()
        bom.pomFile.text = bom.pomFile.text.replace("<version>2.0</version>", "")

        buildFile << """
            dependencies {
                compile "group:moduleA:2.0"
                compile "group:main:5.0"
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:moduleA:2.0")
                module("group:main:5.0")
            }
        }
    }

    def "does not fail for unused dependency entries without version"() {
        given:
        bomDependency('moduleA')
        bomDependency('moduleB')
        bom.publish()
        bom.pomFile.text = bom.pomFile.text.replace("<version>2.0</version>", "")
        buildFile << """
            dependencies {
                compile "group:bom:1.0"
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0").noArtifacts()
            }
        }
    }

    def "fails late for dependency entries that fail to provide a missing version"() {
        given:
        bomDependency('moduleA')
        bomDependency('moduleB')
        bom.publish()
        bom.pomFile.text = bom.pomFile.text.replace("<version>2.0</version>", "")
        buildFile << """
            dependencies {
                compile "group:moduleA"
                compile "group:bom:1.0"
            }
        """

        when:
        fails 'checkDep'

        then:
        failure.assertHasCause "Could not find group:moduleA:."
        failure.assertNotOutput("parse")
    }

    def 'a BOM dependencyManagement entry preserves exclusions declared in build file'() {
        def modB = mavenHttpRepo.module("group", "moduleB", "1.0").allowAll().publish()
        moduleA.dependsOn(modB).publish()
        bomDependency('moduleA').publish()

        buildFile << """
            dependencies {
                compile("group:moduleA") {
                    exclude(group: 'group')
                }
                compile platform("group:bom:1.0")
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0:platform-runtime") {
                    constraint("group:moduleA:2.0", "group:moduleA:2.0")
                    noArtifacts()
                }
                edge("group:moduleA", "group:moduleA:2.0") {
                    byConstraint()
                }
            }
        }
    }

    def 'a BOM dependencyManagement entry preserves transitive=false declared in build file'() {
        def modB = mavenHttpRepo.module("group", "moduleB", "1.0").allowAll().publish()
        moduleA.dependsOn(modB).publish()
        bomDependency('moduleA').publish()

        buildFile << """
            dependencies {
                compile("group:moduleA") {
                    transitive = false
                }
                compile platform("group:bom:1.0")
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0:platform-runtime") {
                    constraint("group:moduleA:2.0", "group:moduleA:2.0")
                    noArtifacts()
                }
                edge("group:moduleA", "group:moduleA:2.0") {
                    byConstraint()
                }
            }
        }
    }
}
