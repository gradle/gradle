/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

class MavenParentPomResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "includes dependencies from parent pom"() {
        given:
        server.start()

        def parentDep = mavenRepo().module("org", "parent_dep").publish()
        def childDep = mavenRepo().module("org", "child_dep").publish()

        def parent = mavenRepo().module("org", "parent")
        parent.type = 'pom'
        parent.dependsOn("parent_dep")
        parent.publish()

        def child = mavenRepo().module("org", "child")
        child.dependsOn("child_dep")
        child.parentPomSection = """
<parent>
  <groupId>org</groupId>
  <artifactId>parent</artifactId>
  <version>1.0</version>
</parent>
"""
        child.publish()

        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        server.expectGet('/repo1/org/child/1.0/child-1.0.pom', child.pomFile)
        server.expectGet('/repo1/org/parent/1.0/parent-1.0.pom', parent.pomFile)

        // Will always check for a default artifact with a module with 'pom' packaging
        server.expectHeadMissing('/repo1/org/parent/1.0/parent-1.0.jar')

        server.expectGet('/repo1/org/child/1.0/child-1.0.jar', child.artifactFile)

        server.expectGet('/repo1/org/parent_dep/1.0/parent_dep-1.0.pom', parentDep.pomFile)
        server.expectGet('/repo1/org/parent_dep/1.0/parent_dep-1.0.jar', parentDep.artifactFile)
        server.expectGet('/repo1/org/child_dep/1.0/child_dep-1.0.pom', childDep.pomFile)
        server.expectGet('/repo1/org/child_dep/1.0/child_dep-1.0.jar', childDep.artifactFile)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar', 'parent_dep-1.0.jar', 'child_dep-1.0.jar')
    }

    def "looks for parent pom in different repository"() {
        given:
        server.start()

        def parent = mavenRepo().module("org", "parent")
        parent.type = 'pom'
        parent.publish()

        def child = mavenRepo().module("org", "child")
        child.parentPomSection = """
<parent>
  <groupId>org</groupId>
  <artifactId>parent</artifactId>
  <version>1.0</version>
</parent>
"""
        child.publish()

        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
    maven { url 'http://localhost:${server.port}/repo2' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        server.expectGet('/repo1/org/child/1.0/child-1.0.pom', child.pomFile)
        server.expectGet('/repo1/org/child/1.0/child-1.0.jar', child.artifactFile)

        server.expectGetMissing('/repo1/org/parent/1.0/parent-1.0.pom')
        server.expectHeadMissing('/repo1/org/parent/1.0/parent-1.0.jar')
        server.expectGet('/repo2/org/parent/1.0/parent-1.0.pom', parent.pomFile)
        server.expectHead('/repo2/org/parent/1.0/parent-1.0.jar', parent.artifactFile)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar')
    }
}