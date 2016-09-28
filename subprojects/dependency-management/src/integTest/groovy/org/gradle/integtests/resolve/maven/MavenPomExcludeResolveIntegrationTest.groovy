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
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class MavenPomExcludeResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile)

    def setup() {
        settingsFile << "rootProject.name='test'"
        resolve.prepare()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3243")
    def "wildcard exclude of groupId and artifactId on a dependency does not include transitive dependencies"() {
        given:
        def notRequired = mavenHttpRepo.module("org.gradle", "bar", "2.0")
        def childDep = mavenHttpRepo.module("com.company", "foo", "1.5")
        childDep.dependsOn(notRequired)
        childDep.publish()
        def parentDep = mavenHttpRepo.module("groupA", "projectA", "1.2")
        parentDep.dependsOn(childDep, exclusions: [[groupId: '*', artifactId: '*']])
        parentDep.publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:projectA:1.2' }
"""

        and:
        parentDep.pom.expectGet()
        parentDep.artifact.expectGet()
        childDep.pom.expectGet()
        childDep.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("groupA:projectA:1.2") {
                    module("com.company:foo:1.5")
                }
            }
        }
    }

    def "can exclude transitive dependencies"() {
        given:
        def notRequired1 = mavenHttpRepo.module("org.gradle", "excluded1", "2.0")
        def notRequired2 = mavenHttpRepo.module("org.gradle", "excluded2", "2.0")
        def m1 = mavenHttpRepo.module("com.company", "m1", "1.5").publish()

        def m2 = mavenHttpRepo.module("com.company", "m2", "1.5")
            .dependsOn(notRequired1)
            .dependsOn(notRequired2)
            .dependsOn(m1)
            .publish()

        def parentDep = mavenHttpRepo.module("groupA", "projectA", "1.2")
            .dependsOn(m2, exclusions: [[groupId: 'org.gradle', artifactId: 'excluded1'], [groupId: 'org.gradle', artifactId: 'excluded2']])
            .publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:projectA:1.2' }
"""

        and:
        parentDep.pom.expectGet()
        parentDep.artifact.expectGet()
        m1.pom.expectGet()
        m1.artifact.expectGet()
        m2.pom.expectGet()
        m2.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("groupA:projectA:1.2") {
                    module("com.company:m2:1.5") {
                        module("com.company:m1:1.5")
                    }
                }
            }
        }
    }
}
