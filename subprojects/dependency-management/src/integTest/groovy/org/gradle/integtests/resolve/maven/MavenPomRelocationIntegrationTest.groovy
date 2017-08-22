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
import spock.lang.Issue

class MavenPomRelocationIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Issue('https://github.com/gradle/gradle/issues/1789')
    def "ignore original artifact when <relocation> exists"() {
        given:
        def original = mavenHttpRepo.module("groupA", "projectA", "1.2").publishPom()
        original.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>projectA</artifactId>
    <version>1.2</version>
    <distributionManagement>
        <relocation>
            <groupId>groupB</groupId>
            <artifactId>projectB</artifactId>
        </relocation>
    </distributionManagement>
</project>
"""

        def newModule = mavenHttpRepo.module("groupB", "projectB", "1.2").publish()

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
        original.pom.expectGet()
        newModule.pom.expectGet()
        newModule.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("libs").assertHasDescendants("projectB-1.2.jar")
    }

    def "retrieve artifact when only groupId in <relocation>"() {
        given:
        def original = mavenHttpRepo.module("groupA", "projectA", "1.2").publishPom()
        original.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>projectA</artifactId>
    <version>1.2</version>
    <distributionManagement>
        <relocation>
            <groupId>newProjectA</groupId>
        </relocation>
    </distributionManagement>
</project>
"""

        def newModule = mavenHttpRepo.module("newProjectA", "projectA", "1.2").publish()

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
        original.pom.expectGet()
        newModule.pom.expectGet()
        newModule.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("libs").assertHasDescendants("projectA-1.2.jar")
    }

    def "fail when artifact in <relocation> doesn't exist"() {
        given:
        def original = mavenHttpRepo.module("groupA", "projectA", "1.2").publishPom()
        original.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>projectA</artifactId>
    <version>1.2</version>
    <distributionManagement>
        <relocation>
            <groupId>notExist</groupId>
            <artifactId>notExist</artifactId>
        </relocation>
    </distributionManagement>
</project>
"""

        def newModule = mavenHttpRepo.module("notExist", "notExist", "1.2")

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
        original.pom.expectGet()
        newModule.pom.expectGet()
        newModule.artifact.expectHead()

        expect:
        fails "retrieve"
        failure.assertHasCause('Could not find notExist:notExist:1.2')
    }
}
