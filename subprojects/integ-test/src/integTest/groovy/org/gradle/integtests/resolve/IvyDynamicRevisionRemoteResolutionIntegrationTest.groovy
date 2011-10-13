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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.junit.Ignore
import org.junit.Rule

class IvyDynamicRevisionRemoteResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    public void "uses latest version from version range"() {
        distribution.requireOwnUserHomeDir()
        server.start()
        def repo = ivyRepo()

        given:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}

configurations { compile }

dependencies {
    compile "group:projectA:1.+"
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        def version1 = repo.module("group", "projectA", "1.1")
        version1.publishArtifact()
        server.expectGetDirectoryListing("/group/projectA/", version1.moduleDir.parentFile)
        server.expectGet("/group/projectA/1.1/ivy-1.1.xml", version1.ivyFile)
        server.expectGet("/group/projectA/1.1/projectA-1.1.jar", version1.jarFile)

        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(version1.jarFile)

        // TODO: Cache dynamic revisions with timeout
        when:
        def version2 = repo.module("group", "projectA", "1.2")
        version2.publishArtifact()
        server.expectGetDirectoryListing("/group/projectA/", version1.moduleDir.parentFile)
        server.expectGet("/group/projectA/1.2/ivy-1.2.xml", version2.ivyFile)
        server.expectGet("/group/projectA/1.2/projectA-1.2.jar", version2.jarFile)
        // TODO: This should not be required (even when not cached)
        server.expectGet("/group/projectA/1.1/ivy-1.1.xml", version1.ivyFile)

        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(version2.jarFile)
    }


    @Ignore
    public void "uses cached dynamic revision resolved from an ivy HTTP repository until the timeout is reached"() {
        distribution.requireOwnUserHomeDir()
        server.start()

        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}

configurations { compile }

dependencies {
    compile "group:projectA:1.+"
}

task retrieve(type: Sync) {
    into 'build'
    from configurations.compile
}
"""

        // Publish the first snapshot
        def module = ivyRepo().module("group", "projectA", "1.1")
        module.publishArtifact()

        // Retrieve the first snapshot
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.ivyFile)

        run 'retrieve'

        def jarFile = file('build/projectA-1.1.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        // Publish a newer version
        def module2 = ivyRepo().module("group", "projectA", "1.2")
        module2.publishArtifact()

        server.resetExpectations()
        // TODO - these should not be here

        // Retrieve again should use cached snapshot, and should not hit the server
        executer.withTasks('retrieve').run().assertTasksSkipped(':retrieve')
        jarFile.assertHasNotChangedSince(snapshot)

        // Retrieve again with zero timeout should download and use updated snapshot
        server.resetExpectations()
        executer.withTasks('retrieve').withArguments("-PnoTimeout").run().assertTasksNotSkipped(':retrieve')
        jarFile.assertIsCopyOf(module2.jarFile)
    }

    IvyRepository ivyRepo() {
        return new IvyRepository(distribution.testFile('ivy-repo'))
    }
}
