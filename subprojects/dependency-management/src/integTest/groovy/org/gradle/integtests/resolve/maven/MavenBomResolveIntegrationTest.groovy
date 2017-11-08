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

class MavenBomResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile)
    MavenModule bom

    def setup() {
        //TODO these requests should not happen anymore with https://github.com/gradle/gradle/pull/3432
        mavenHttpRepo.module('group', 'moduleA', '').missing()

        resolve.prepare()
        settingsFile << """
            rootProject.name = 'testproject'
        """
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
        mavenHttpRepo.module('group', 'moduleA', '2.0').allowAll().publish()

        buildFile << """
            dependencies {
                compile "group:moduleA"
            }
        """
    }

    def "can use a bom to select a version"() {
        given:
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
                module("group:bom:1.0", {
                    module("group:moduleA:2.0")
                }).noArtifacts()
                edge("group:moduleA:", "group:moduleA:2.0").byConflictResolution()
            }
        }
    }

    def "can import a bom transitively"() {
        given:
        mavenHttpRepo.module('group', 'main', '5.0').allowAll().dependsOn(bom).publish()

        buildFile << """
            dependencies {
                compile "group:main:5.0"
            }
        """

        when:
        succeeds 'checkDep'

        then:
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("group:main:5.0") {
                    module("group:bom:1.0", {
                        module("group:moduleA:2.0")
                    }).noArtifacts()
                }
                edge("group:moduleA:", "group:moduleA:2.0").byConflictResolution()
            }
        }
    }

    def "a parent pom is not a bom"() {
        mavenHttpRepo.module('group', 'main', '5.0').allowAll().parent(bom.group, bom.artifactId, bom.version).publish()

        buildFile << """
            dependencies {
                compile "group:main:5.0"
            }
        """

        when:
        fails 'checkDep'

        then:
        //TODO cause is expected to change with https://github.com/gradle/gradle/pull/3432
        failure.assertHasCause "Could not find group:moduleA:." //"No version specified for group:moduleA"
    }
}
