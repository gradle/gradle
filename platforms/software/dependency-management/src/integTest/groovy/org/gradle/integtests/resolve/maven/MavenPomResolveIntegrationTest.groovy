/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class MavenPomResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "compile")

    def setup() {
        settingsFile """
            rootProject.name = 'test'
        """
    }

    def "follows relocation to another group"() {
        given:
        def original = mavenHttpRepo.module("groupA", "projectA", "1.2").publishPom()
        original.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>projectA</artifactId>
    <version>1.2</version>
    <distributionManagement>
        <relocation>
            <groupId>newGroupA</groupId>
        </relocation>
    </distributionManagement>
</project>
"""

        def newModule = mavenHttpRepo.module("newGroupA", "projectA", "1.2").publish()
        newModule.publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:projectA:1.2' }
"""
        resolve.prepare()

        and:
        original.pom.expectGet()
        newModule.pom.expectGet()
        newModule.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("groupA:projectA:1.2") {
                    noArtifacts()
                    module("newGroupA:projectA:1.2") // relocation is treated as a dependency in the graph
                }
            }
        }
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2861")
    def "can handle pom with unspecified properties in dependency management"() {
        given:
        def parent = mavenHttpRepo.module('group', 'parent', '1.0').publish()
        parent.pomFile.text = parent.pomFile.text.replace("</project>", '''
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>${some.group}</groupId>
            <artifactId>${some.artifact}</artifactId>
            <version>${some.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
</project>
''')

        def dep = mavenHttpRepo.module('group', 'artifact', '1.0').parent('group', 'parent', '1.0').publish()

        and:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations { compile }
            dependencies {
                compile "group:artifact:1.0"
            }
        """
        resolve.prepare()

        and:
        parent.pom.expectGet()
        dep.pom.expectGet()
        dep.artifact.expectGet()

        expect:
        // have to run twice to trigger the failure, to parse the descriptor from the cache
        succeeds ":checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:artifact:1.0")
            }
        }

        succeeds ":checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:artifact:1.0")
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/22279")
    def "can resolve POM as an artifact after a relocation"() {
        given:
        def original = mavenHttpRepo.module('groupA', 'artifactA', '1.0').publishPom()
        original.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.0</version>
    <distributionManagement>
        <relocation>
            <groupId>groupA</groupId>
            <artifactId>artifactA</artifactId>
            <version>2.0</version>
        </relocation>
    </distributionManagement>
</project>
"""

        def newModule = mavenHttpRepo.module('groupA', 'artifactA', '2.0').publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations {
    first
    second
}
dependencies {
    first 'groupA:artifactA:1.0'
    second 'groupA:artifactA:2.0@pom'
}

task retrieve {
    def files = configurations.first
    doLast {
        // populates cache for POM artifact in memory
        files*.name
    }
}

"""
        resolve.prepare("second")

        and:
        original.pom.expectGet()
        newModule.pom.expectGet()

        expect:
        succeeds "retrieve", "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("groupA:artifactA:2.0") {
                    artifact(type: "pom")
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/22279")
    @ToBeFixedForConfigurationCache(because = "task uses Artifact Query API")
    def "can resolve POM as an artifact after it was resolved via ARQ"() {
        given:
        def original = mavenHttpRepo.module('groupA', 'artifactA', '1.0').publishPom()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations {
    conf
}
dependencies {
    conf 'groupA:artifactA:1.0@pom'
}

task retrieve {
    doLast {
        // populates cache for POM artifact in memory
        def result = dependencies.createArtifactResolutionQuery()
                                    .forModule("groupA", "artifactA", "1.0")
                                    .withArtifacts(MavenModule, MavenPomArtifact)
                                    .execute()
        for (component in result.resolvedComponents) {
            component.getArtifacts(MavenPomArtifact).each { println "POM for " + component }
        }
    }
}
"""
        resolve.prepare("conf")

        and:
        original.pom.expectGet()

        expect:
        succeeds "retrieve", "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("groupA:artifactA:1.0") {
                    artifact(type: "pom")
                }
            }
        }
    }
}
