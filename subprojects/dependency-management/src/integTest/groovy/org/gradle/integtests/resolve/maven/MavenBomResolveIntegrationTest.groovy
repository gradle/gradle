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
import org.gradle.integtests.fixtures.ExperimentalFeaturesFixture
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.maven.MavenModule

class MavenBomResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile).expectDefaultConfiguration('runtime')
    MavenModule bom
    MavenModule moduleA

    def setup() {
        resolve.prepare()
        ExperimentalFeaturesFixture.enable(settingsFile)
        settingsFile << "rootProject.name = 'testproject'"
        buildFile << """
            repositories { maven { url "${mavenHttpRepo.uri}" } }
            configurations { compile }
        """
        bom = mavenHttpRepo.module('group', 'bom', '1.0').hasType("pom").allowAll().publish()
        bom.pomFile.text = bom.pomFile.text.replace("</project>", '''
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group</groupId>
                        <artifactId>moduleA</artifactId>
                        <version>2.0</version>
                    </dependency>
                    <dependency>
                        <groupId>group</groupId>
                        <artifactId>moduleB</artifactId>
                        <version>2.0</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </project>
        ''')
        moduleA = mavenHttpRepo.module('group', 'moduleA', '2.0').allowAll().publish()
    }

    def "can use a bom to select a version"() {
        given:
        buildFile << """
            dependencies {
                compile "group:moduleA"
                compile "group:bom:1.0"
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0") {
                    module("group:moduleA:2.0")
                    noArtifacts()
                }
                edge("group:moduleA:", "group:moduleA:2.0")
            }
        }
    }

    def "can import a bom transitively"() {
        given:
        mavenHttpRepo.module('group', 'main', '5.0').allowAll().dependsOn(bom).publish()

        buildFile << """
            dependencies {
                compile "group:moduleA"
                compile "group:main:5.0"
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:main:5.0") {
                    module("group:bom:1.0") {
                        module("group:moduleA:2.0")
                        noArtifacts()
                    }
                }
                edge("group:moduleA:", "group:moduleA:2.0")
            }
        }
    }

    def "a bom can declare excludes"() {
        given:
        moduleA.dependsOn(mavenHttpRepo.module("group", "moduleC", "1.0").allowAll().publish()).publish()
        bom.pomFile.text = bom.pomFile.text.replace("<version>2.0</version>", '''
                        <version>2.0</version>
                        <exclusions>
                            <exclusion>
                                <groupId>group</groupId>
                                <artifactId>moduleC</artifactId>
                            </exclusion>
                        </exclusions>
        ''')

        buildFile << """
            dependencies {
                compile("group:moduleA") {
                    exclude(group: 'group')
                }
                compile "group:bom:1.0"
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0") {
                    module("group:moduleA:2.0")
                    noArtifacts()
                }
                edge("group:moduleA:", "group:moduleA:2.0")
            }
        }

        when:
        //we remove the exclude in the build script: the excludes are merged and the one in the bom has no effect anymore
        buildFile.text = buildFile.text.replace("exclude(group: 'group')", "")
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0") {
                    module("group:moduleA:2.0") {
                        module("group:moduleC:1.0")
                    }
                    noArtifacts()
                }
                edge("group:moduleA:", "group:moduleA:2.0")
            }
        }
    }

    def "a bom can declare dependencies"() {
        given:
        mavenHttpRepo.module('group', 'moduleC', '1.0').allowAll().publish()
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
                compile "group:bom:1.0"
                compile "group:moduleA"
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:bom:1.0") {
                    module("group:moduleA:2.0")
                    module("group:moduleC:1.0")
                    noArtifacts()
                }
                edge("group:moduleA:", "group:moduleA:2.0")
            }
        }

    }

    def "a parent pom is not a bom"() {
        mavenHttpRepo.module('group', 'main', '5.0').allowAll().parent(bom.group, bom.artifactId, bom.version).publish()

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
        !failure.error.contains("Could not parse POM ${mavenHttpRepo.uri}/group/bom/1.0/bom-1.0.pom")
    }
}
