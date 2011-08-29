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
package org.gradle.integtests

import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.HttpServer
import org.junit.Rule

class IvyRemoteDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()

    public void "can resolve and cache dependencies from an HTTP Ivy repository"() {
        distribution.requireOwnUserHomeDir()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publishArtifact()

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)
        server.start()

        and:
        buildFile << """
repositories {
    ivy {
        name = 'gradleReleases'
        artifactPattern "http://localhost:${server.port}/repo/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        expect:
        succeeds('listJars')
        succeeds('listJars')
    }

    public void "can resolve and cache dependencies from multiple HTTP Ivy repositories"() {
        distribution.requireOwnUserHomeDir()

        given:
        def repo = ivyRepo()
        def module1 = repo.module('group', 'projectA', '1.2')
        module1.publishArtifact()
        def module2 = repo.module('group', 'projectB', '1.3')
        module2.publishArtifact()

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module1.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module1.jarFile)
        server.expectGetMissing('/repo/group/projectB/1.3/ivy-1.3.xml')
        server.expectGet('/repo2/group/projectB/1.3/ivy-1.3.xml', module2.ivyFile)
        server.expectGet('/repo2/group/projectB/1.3/projectB-1.3.jar', module2.jarFile)

        // TODO - this shouldn't happen - it's already found B's ivy.xml on repo2
        server.expectGetMissing('/repo/group/projectB/1.3/projectB-1.3.jar')
        server.expectGetMissing('/repo/group/projectB/1.3/projectB-1.3.jar')

        server.start()

        and:
        buildFile << """
repositories {
    ivy {
        name = 'gradleReleases'
        artifactPattern "http://localhost:${server.port}/repo/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
    ivy {
        name = 'otherReleases'
        artifactPattern "http://localhost:${server.port}/repo2/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2', 'group:projectB:1.3'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar', 'projectB-1.3.jar']
}
"""

        expect:
        succeeds('listJars')

//        given:
        // TODO - it should not be doing these
        server.expectGetMissing('/repo/group/projectB/1.3/ivy-1.3.xml')
        server.expectGetMissing('/repo/group/projectB/1.3/projectB-1.3.jar')
        server.expectGetMissing('/repo/group/projectB/1.3/projectB-1.3.jar')

//        expect:
        succeeds('listJars')
    }

    public void "can resolve dependencies from password protected HTTP Ivy repository"() {
        distribution.requireOwnUserHomeDir()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publishArtifact()

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', module.jarFile)
        server.start()

        and:
        buildFile << """
repositories {
    ivy {
        name = 'gradleReleases'
        userName = 'username'
        password = 'password'
        artifactPattern "http://localhost:${server.port}/repo/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        expect:
        succeeds('listJars')
    }

    public void "reports missing and failed HTTP downloads"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy {
        name = 'gradleReleases'
        artifactPattern "http://localhost:${server.port}/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
    }
}
configurations { compile }
dependencies {
    compile 'group:org:1.2'
}
task show << { println configurations.compile.files }
"""

        when:
        server.expectGetMissing('/org/1.2/ivy-1.2.xml')
        server.expectGetMissing('/org/1.2/org-1.2.jar')
        fails("show")

        then:
        failure.assertHasDescription('Execution failed for task \':show\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\':')
        assert failure.getOutput().contains('group#org;1.2: not found')

        when:
        server.addBroken('/')
        fails("show")

        then:
        failure.assertHasDescription('Execution failed for task \':show\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\':')
    }

    IvyRepository ivyRepo() {
        return new IvyRepository(file('ivy-repo'))
    }
}
