/*
 * Copyright 2012 the original author or authors.
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

class MavenFileRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {
    void "can resolve snapshots uncached from local Maven repository"() {
        given:
        def moduleA = mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT')
        def moduleB = mavenRepo().module('group', 'projectB', '9.1')
        moduleA.publish()
        moduleB.publish()

        and:
        buildFile << """
configurations { compile }
repositories { maven { url "${mavenRepo().uri}" } }
dependencies { compile 'group:projectA:1.2-SNAPSHOT' }
task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants(moduleA.artifactFile.name)
        buildDir.file(moduleA.artifactFile.name).assertIsCopyOf(moduleA.artifactFile)

        when:
        moduleA.dependsOn('group', 'projectB', '9.1')
        moduleA.publishWithChangedContent()
        run 'retrieve'

        then:
        buildDir.assertHasDescendants(moduleA.artifactFile.name, 'projectB-9.1.jar')
        buildDir.file(moduleA.artifactFile.name).assertIsCopyOf(moduleA.artifactFile)
        buildDir.file('projectB-9.1.jar').assertIsCopyOf(moduleB.artifactFile)
    }

    void "does not cache artifacts and metadata from local Maven repository"() {
        given:
        def moduleA = mavenRepo().module('group', 'projectA', '1.2')
        def moduleB = mavenRepo().module('group', 'projectB', '9.1')
        moduleA.publish()
        moduleB.publish()

        and:
        buildFile << """
configurations { compile }
repositories { maven { url "${mavenRepo().uri}" } }
dependencies { compile 'group:projectA:1.2' }
task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants('projectA-1.2.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(moduleA.artifactFile)

        when:
        moduleA.dependsOn('group', 'projectB', '9.1')
        moduleA.publishWithChangedContent()
        run 'retrieve'

        then:
        buildDir.assertHasDescendants('projectA-1.2.jar', 'projectB-9.1.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(moduleA.artifactFile)
        buildDir.file('projectB-9.1.jar').assertIsCopyOf(moduleB.artifactFile)
    }

    void "uses artifactUrls to resolve artifacts"() {
        given:
        def moduleA = mavenRepo().module('group', 'projectA', '1.2')
        def moduleB = mavenRepo().module('group', 'projectB', '9.1')
        moduleA.publish()
        moduleB.publish()

        def artifactsRepo = mavenRepo('artifactsRepo')
        // Create a module to get the correct module directory, but do not publish the module
        def artifactsModuleA = artifactsRepo.module('group', 'projectA', '1.2')
        moduleA.artifactFile.moveToDirectory(artifactsModuleA.moduleDir)

        and:
        buildFile << """
repositories {
    maven {
        url "${mavenRepo().uri}"
        artifactUrls "${artifactsRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
    compile 'group:projectB:9.1'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants('projectA-1.2.jar', 'projectB-9.1.jar')
        buildDir.file('projectA-1.2.jar').assertIsCopyOf(artifactsModuleA.artifactFile)
        buildDir.file('projectB-9.1.jar').assertIsCopyOf(moduleB.artifactFile)
    }

    def "cannot define authentication for local file repo"() {
        given:
        def repo = mavenRepo()
        def moduleA = repo.module('group', 'projectA', '1.2')
        moduleA.publish()
        and:
        buildFile << """
repositories {
    maven {
        url "${repo.uri}"
        authentication {
            auth(BasicAuthentication)
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        expect:
        fails 'retrieve'
        and:
        failure.assertHasCause("Authentication scheme 'auth'(BasicAuthentication) is not supported by protocol 'file'")
    }
}
