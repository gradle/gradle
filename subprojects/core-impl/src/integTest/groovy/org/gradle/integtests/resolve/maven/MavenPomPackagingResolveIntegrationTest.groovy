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
import org.gradle.test.fixtures.maven.MavenFileModule
import spock.lang.FailsWith
import spock.lang.Issue

class MavenPomPackagingResolveIntegrationTest extends AbstractDependencyResolutionTest {
    MavenFileModule projectA

    def setup() {
        server.start()

        projectA = mavenRepo().module('group', 'projectA')
    }

    private void buildWithDependencies(def dependencies) {
        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
    maven { url 'http://localhost:${server.port}/repo2' }
}
configurations { compile }
dependencies {
    $dependencies
}
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
    }

    def "looks for jar artifact for pom with packaging of type 'pom' in the same repository only"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        publishWithPackaging('pom')

        and:
        // First attempts to resolve in repo1
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom')
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.jar')

        server.expectGet('/repo2/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectHead('/repo2/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo2/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        def snapshot = file('libs/projectA-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        and:
        run 'retrieve'

        then: // Uses cached artifacts
        file('libs/projectA-1.0.jar').assertHasNotChangedSince(snapshot)
    }

    def "will use jar artifact for pom with packaging that maps to jar"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        publishWithPackaging(packaging)

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        file('libs/projectA-1.0.jar').assertIsCopyOf(projectA.artifactFile)

        where:
        packaging << ['', 'jar', 'eclipse-plugin', 'bundle']
    }


    @Issue('GRADLE-2188')
    def "will use jar artifact for pom with packaging 'orbit'"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        publishWithPackaging('orbit')

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.orbit')
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        file('libs/projectA-1.0.jar').assertIsCopyOf(projectA.artifactFile)
    }

    @Issue('GRADLE-2188')
    def "where 'module.custom' exists, will use it as main artifact for pom with packaging 'custom' and emit deprecation warning"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        publishWithPackaging('custom', 'custom')

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectHead('/repo1/group/projectA/1.0/projectA-1.0.custom', projectA.artifactFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.custom', projectA.artifactFile)

        and:
        executer.withDeprecationChecksDisabled()

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.custom')
        file('libs/projectA-1.0.custom').assertIsCopyOf(projectA.artifactFile)

        and:
        result.output.contains("Relying on packaging to define the extension of the main artifact has been deprecated")
    }

    def "fails and reports type-based location if neither packaging-based or type-based artifact can be located"() {
        when:
        buildWithDependencies("compile 'group:projectA:1.0'")
        publishWithPackaging('custom')

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.custom')
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.jar')

        then:
        fails 'retrieve'

        and:
        result.error.contains("Artifact 'group:projectA:1.0@jar' not found.")
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
        publishWithPackaging('custom')

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)

        // TODO:GRADLE-2188 This call should not be required, since "type='zip'" on the dependency alleviates the need to check for the packaging artifact
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.custom')
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.zip', projectA.artifactFile)

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.zip')
        file('libs/projectA-1.0.zip').assertIsCopyOf(projectA.artifactFile)
    }

    def "will use non-jar maven dependency type to determine artifact location"() {
        when:
        buildWithDependencies("""
compile 'group:mavenProject:1.0'
""")
        def mavenProject = mavenRepo().module('group', 'mavenProject', '1.0').hasType('pom').dependsOn('group', 'projectA', '1.0', 'zip').publish()
        publishWithPackaging('custom', 'zip')

        and:
        server.expectGet('/repo1/group/mavenProject/1.0/mavenProject-1.0.pom', mavenProject.pomFile)
        server.expectHeadMissing('/repo1/group/mavenProject/1.0/mavenProject-1.0.jar')
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)

        // TODO:GRADLE-2188 This call should not be required, since "type='zip'" on the dependency alleviates the need to check for the packaging artifact
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.custom')
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.zip', projectA.artifactFile)

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.zip')
        file('libs/projectA-1.0.zip').assertIsCopyOf(projectA.artifactFile)
    }

    @FailsWith(value = AssertionError, reason = "Pending better fix for GRADLE-2188")
    def "does not emit deprecation warning if dependency type is used to locate artifact, even if custom packaging matches file extension"() {
        when:
        buildWithDependencies("""
compile('group:projectA:1.0') {
    artifact {
        name = 'projectA'
        type = 'zip'
    }
}
""")
        publishWithPackaging('zip')

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.zip', projectA.artifactFile)

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.zip')
        file('libs/projectA-1.0.zip').assertIsCopyOf(projectA.artifactFile)

        and: "Stop the http server here to allow failure to be declared (otherwise occurs in tearDown) - remove this when the test is fixed"
        server.stop()
    }

    private def publishWithPackaging(String packaging, String type = 'jar') {
        projectA.packaging = packaging
        projectA.type = type
        projectA.publish()
    }

}