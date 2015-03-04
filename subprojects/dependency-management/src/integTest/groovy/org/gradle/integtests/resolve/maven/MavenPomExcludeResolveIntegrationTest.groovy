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
import spock.lang.Issue

class MavenPomExcludeResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Issue("https://issues.gradle.org/browse/GRADLE-3243")
    def "Wildcard exclude of groupId and artifactId does not exclude itself"() {
        given:
        mavenHttpRepo.module("org.gradle", "bar", "2.0").publish()
        def childDep = mavenHttpRepo.module("com.company", "foo", "1.5")
        childDep.dependsOn("org.gradle", "bar", "2.0")
        childDep.publish()
        def parentDep = mavenHttpRepo.module("groupA", "projectA", "1.2")
        parentDep.dependsOn("com.company", "foo", "1.5")
        parentDep.publish()
        parentDep.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>projectA</artifactId>
    <version>1.2</version>
    <dependencies>
        <dependency>
            <groupId>com.company</groupId>
            <artifactId>foo</artifactId>
            <version>1.5</version>
            <exclusions>
                <exclusion>
                    <groupId>*</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
</project>
"""

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:projectA:1.2' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and:
        parentDep.pom.expectGet()
        parentDep.artifact.expectGet()
        childDep.pom.expectGet()
        childDep.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("libs").assertHasDescendants("projectA-1.2.jar", "foo-1.5.jar")
    }
}
