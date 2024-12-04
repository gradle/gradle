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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import spock.lang.Issue

class MavenPomPackagingResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    MavenHttpRepository repo1
    MavenHttpRepository repo2
    MavenHttpModule projectARepo1
    MavenHttpModule projectARepo2

    def setup() {
        repo1 = mavenHttpRepo("repo1")
        repo2 = mavenHttpRepo("repo2")
        projectARepo1 = repo1.module('group', 'projectA')
        projectARepo2 = repo2.module('group', 'projectA')
    }

    private void buildWithDependencies(def dependencies) {
        buildFile << """
repositories {
    maven { url = '${repo1.uri}' }
    maven { url = '${repo2.uri}' }
}
configurations { compile }
dependencies {
    $dependencies
}
task deleteDir(type: Delete) {
    delete 'libs'
}
task retrieve(type: Copy, dependsOn: deleteDir) {
    into 'libs'
    from configurations.compile
}
"""
    }

    def "includes jar artifact if present for pom with packaging of type 'pom'"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        projectARepo2.hasPackaging("pom").publish()

        and:
        // First attempts to resolve in repo1
        projectARepo1.pom.expectGetMissing()

        projectARepo2.pom.expectGet()
        projectARepo2.artifact.expectHead()
        projectARepo2.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')

        when:
        server.resetExpectations()
        run 'retrieve'

        then: // Uses cached artifacts
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "ignores missing jar artifact for pom with packaging of type 'pom'"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        projectARepo1.hasPackaging("pom").publishPom()

        and:
        projectARepo1.pom.expectGet()
        projectARepo1.artifact.expectHeadMissing()

        and:
        run 'retrieve'

        then:
        file('libs').assertDoesNotExist()

        when:
        server.resetExpectations()
        run 'retrieve'

        then: // Uses cached artifacts
        file('libs').assertDoesNotExist()
    }

    def "for a snapshot module with packaging of type 'pom', will check for jar artifact that was previously missing on cache expiry"() {
        when:
        def snapshotA = repo1.module('group', 'projectA', '1.1-SNAPSHOT')
        snapshotA.hasPackaging("pom").publish()

        and:
        buildWithDependencies("compile 'group:projectA:1.1-SNAPSHOT'")
        buildFile << """
if (project.hasProperty('skipCache')) {
    configurations.compile.resolutionStrategy.cacheChangingModulesFor(0, 'seconds')
}
"""

        and:
        snapshotA.metaData.expectGet()
        snapshotA.pom.expectGet()
        snapshotA.artifact.expectHeadMissing()

        and:
        run 'retrieve'

        then:
        skipped ':retrieve'

        // Jar artifact presence is cached
        when:
        server.resetExpectations()

        and:
        run 'retrieve'

        then:
        skipped ':retrieve'

        // New artifact is detected
        when:
        server.resetExpectations()
        snapshotA.metaData.expectHead()
        snapshotA.pom.expectHead()
        snapshotA.artifact.expectHead()
        snapshotA.artifact.expectGet()

        and:
        executer.withArgument('-PskipCache')
        run 'retrieve'

        then:
        executed ':retrieve'
        file('libs').assertHasDescendants('projectA-1.1-SNAPSHOT.jar')

        // Jar artifact removal is detected
        when:
        server.resetExpectations()
        snapshotA.metaData.expectHead()
        snapshotA.pom.expectHead()
        snapshotA.artifact.expectHeadMissing()

        and:
        executer.withArgument('-PskipCache')
        run 'retrieve'

        then:
        skipped ':retrieve'
    }

    def "will use jar artifact for pom with packaging (#packaging) that maps to jar"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        projectARepo1.hasPackaging(packaging).publish()

        and:
        projectARepo1.pom.expectGet()
        projectARepo1.artifact.expectGet()

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        file('libs/projectA-1.0.jar').assertIsCopyOf(projectARepo1.artifactFile)

        // Check caching
        when:
        server.resetExpectations()
        then:
        succeeds 'retrieve'

        where:
        packaging << ['', 'jar', 'eclipse-plugin', 'bundle']
    }


    @Issue('GRADLE-2188')
    def "will use jar artifact for pom with packaging 'orbit'"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        projectARepo1.hasPackaging('orbit').publish()

        and:
        projectARepo1.pom.expectGet()
        projectARepo1.artifact(type: 'orbit').expectHeadMissing()
        projectARepo1.artifact.expectGet()

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        file('libs/projectA-1.0.jar').assertIsCopyOf(projectARepo1.artifactFile)

        // Check caching
        when:
        server.resetExpectations()
        then:
        succeeds 'retrieve'
    }

    def "fails and reports type-based location if neither packaging-based or type-based artifact can be located"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        projectARepo1.hasPackaging("custom").publishPom()

        and:
        projectARepo1.pom.expectGet()
        projectARepo1.artifact(type: 'custom').expectHeadMissing()
        projectARepo1.artifact(type: 'jar').expectGetMissing()

        then:
        fails 'retrieve'

        and:
        // TODO - should report both locations as missing
        failure.assertHasCause("Could not find projectA-1.0.jar (group:projectA:1.0).")
    }

    def "will use non-jar dependency type to determine jar artifact location"() {
        when:
        buildWithDependencies("""
compile('group:projectA:1.0') {
    artifact {
        name = 'projectA'
        type = 'zip'
    }
}
""")
        projectARepo1.hasPackaging("custom").hasType("custom")
        projectARepo1.artifact(type: 'zip')
        projectARepo1.publish()

        and:
        projectARepo1.pom.expectGet()
        projectARepo1.artifact(type: 'zip').expectGet()

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.zip')
        file('libs/projectA-1.0.zip').assertIsCopyOf(projectARepo1.artifact(type: 'zip').file)

        // Check caching
        when:
        server.resetExpectations()
        then:
        succeeds 'retrieve'
    }

    def "will use non-jar maven dependency type to determine artifact location"() {
        when:
        buildWithDependencies("""
compile 'group:mavenProject:1.0'
""")
        def mavenProject = repo1.module('group', 'mavenProject', '1.0').hasPackaging('pom')
                .dependsOn('group', 'projectA', '1.0', 'zip', 'compile', null).publishPom()
        projectARepo1.hasPackaging("custom")
        projectARepo1.artifact(type: 'zip')
        projectARepo1.publish()

        and:
        mavenProject.pom.expectGet()
        mavenProject.artifact.expectHeadMissing()

        projectARepo1.pom.expectGet()
        projectARepo1.artifact(type: 'zip').expectGet()

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.zip')
        file('libs/projectA-1.0.zip').assertIsCopyOf(projectARepo1.artifact(type: 'zip').file)

        // Check caching
        when:
        server.resetExpectations()
        then:
        succeeds 'retrieve'
    }

    def "will use dependency type to locate artifact, even when custom packaging matches artifact type"() {
        when:
        buildWithDependencies("""
compile('group:projectA:1.0') {
    artifact {
        name = 'projectA'
        type = 'zip'
    }
}
""")
        projectARepo1.hasPackaging("zip").hasType("zip").publish()

        and:
        projectARepo1.pom.expectGet()
        projectARepo1.artifact.expectGet()

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.zip')
        file('libs/projectA-1.0.zip').assertIsCopyOf(projectARepo1.artifactFile)

        // Check caching
        when:
        server.resetExpectations()
        then:
        succeeds 'retrieve'
    }

}
