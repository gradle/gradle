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
import org.junit.Rule
import spock.lang.Ignore

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
    compile group: "group", name: "projectA", version: "1.+"
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

    // This doesn't work as expected - need to implement our own dynamic revision mechanism
    @Ignore
    public void "detects changed artifact when flagged as changing"() {
        distribution.requireOwnUserHomeDir()
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}

configurations { compile }

dependencies {
    compile group: "group", name: "projectA", version: "1.1", changing: true
}

task retrieve(type: Copy) {
    into 'build'
    from configurations.compile
}
"""

        and:
        def module = ivyRepo().module("group", "projectA", "1.1")
        module.publishArtifact()

        when:
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)

        run 'retrieve'

        then:
        def jarFile = file('build/projectA-1.1.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when:
        module.publishWithChangedContent()
        // TODO: Should cache with a timeout: check that the cached file is used prior to timeout

        server.resetExpectations()
        // Server will be hit to get updated versions
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)

        run 'retrieve'

        then:
        jarFile.assertHasChangedSince(snapshot)
        jarFile.assertIsCopyOf(module.jarFile)

    }

    IvyRepository ivyRepo() {
        return new IvyRepository(distribution.testFile('ivy-repo'))
    }
}
