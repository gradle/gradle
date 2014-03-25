/*
 * Copyright 2014 the original author or authors.
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

class MavenProfileResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def "uses properties from active profile to resolve dependency"() {
        server.start()

        given:
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
    <dependencies>
        <dependency>
            <groupId>\${groupId.prop}</groupId>
            <artifactId>\${artifactId.prop}</artifactId>
            <version>\${version.prop}</version>
        </dependency>
    </dependencies>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>groupB</groupId.prop>
                <artifactId.prop>artifactB</artifactId.prop>
                <version.prop>1.4</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.4").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("libs").assertHasDescendants("artifactA-1.2.jar", "artifactB-1.4.jar")
    }

    def "uses dependency management defaults from active profile to resolve dependency"() {
        server.start()

        given:
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
    <dependencies>
        <dependency>
            <groupId>groupB</groupId>
            <artifactId>artifactB</artifactId>
        </dependency>
    </dependencies>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>groupB</groupId>
                        <artifactId>artifactB</artifactId>
                        <version>1.4</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.4").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("libs").assertHasDescendants("artifactA-1.2.jar", "artifactB-1.4.jar")
    }
}
