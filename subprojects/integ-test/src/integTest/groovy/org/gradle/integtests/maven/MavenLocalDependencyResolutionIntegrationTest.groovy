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
package org.gradle.integtests.maven

import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.gradle.util.TextUtil

class MavenLocalDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    public void "can resolve snapshots from local Maven repository"() {
        distribution.requireOwnUserHomeDir()

        given:
        def moduleA = repo().module('group', 'projectA', '1.2-SNAPSHOT')
        def moduleB = repo().module('group', 'projectB', '9.1')
        moduleA.publishArtifact()
        moduleB.publishArtifact()

        and:
        buildFile << """
configurations { compile }
repositories { mavenRepo urls: "${TextUtil.escapeString(repo().rootDir.toURI())}" }
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

    public void "does not cache artifacts and metadata from local Maven repository"() {
        distribution.requireOwnUserHomeDir()

        given:
        def moduleA = repo().module('group', 'projectA', '1.2')
        def moduleB = repo().module('group', 'projectB', '9.1')
        moduleA.publishArtifact()
        moduleB.publishArtifact()

        and:
        buildFile << """
configurations { compile }
repositories { mavenRepo urls: "${TextUtil.escapeString(repo().rootDir.toURI())}" }
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

    MavenRepository repo() {
        return new MavenRepository(distribution.testFile('repo'))
    }
}
