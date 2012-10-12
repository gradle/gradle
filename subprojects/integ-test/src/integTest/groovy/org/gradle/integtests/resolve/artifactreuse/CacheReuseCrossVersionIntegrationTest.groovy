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
package org.gradle.integtests.resolve.artifactreuse

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.TargetVersions
import org.junit.Rule

@TargetVersions('1.0-milestone-6+')
class CacheReuseCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    def "uses cached artifacts from previous Gradle version when no sha1 header"() {
        given:
        def projectB = mavenRepo.module('org.name', 'projectB').publish()
        server.sendSha1Header = false
        server.start()
        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}' }
}
configurations { compile }
dependencies {
    compile 'org.name:projectB:1.0'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
        and:
        def userHome = file('user-home')

        when:
        server.allowGetOrHead('/org', mavenRepo.rootDir.file('org'))

        and:
        version previous withUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        def snapshot = file('libs/projectB-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        projectB.allowPomHead(server)
        projectB.allowPomSha1GetOrHead(server)
        projectB.allowArtifactHead(server)
        projectB.allowArtifactSha1GetOrHead(server)

        and:
        version current withUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        file('libs/projectB-1.0.jar').assertContentsHaveNotChangedSince(snapshot)
    }

    def "uses cached artifacts from previous Gradle version with sha1 header"() {
        given:
        def projectB = mavenRepo.module('org.name', 'projectB').publish()
        server.sendSha1Header = true
        server.start()
        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}' }
}
configurations { compile }
dependencies {
    compile 'org.name:projectB:1.0'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
        and:
        def userHome = file('user-home')

        when:
        server.allowGetOrHead('/org', mavenRepo.rootDir.file('org'))

        and:
        version previous withUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        def snapshot = file('libs/projectB-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        projectB.allowPomHead(server)
        projectB.allowArtifactHead(server)

        and:
        version current withUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        file('libs/projectB-1.0.jar').assertContentsHaveNotChangedSince(snapshot)
    }
}
