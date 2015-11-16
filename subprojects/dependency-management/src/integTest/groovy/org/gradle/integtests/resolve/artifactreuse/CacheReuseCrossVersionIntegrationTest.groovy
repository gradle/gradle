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

import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

import org.gradle.integtests.fixtures.IgnoreVersions
import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager

@TargetVersions('1.0-milestone-6+')
@IgnoreVersions({ it.artifactCacheLayoutVersion == DefaultCacheLockingManager.CACHE_LAYOUT_VERSION || it.version.version == "1.9-rc-1" })
@LeaksFileHandles
class CacheReuseCrossVersionIntegrationTest extends AbstractCacheReuseCrossVersionIntegrationTest {
    @Rule public final HttpServer server = new HttpServer()
    final MavenHttpRepository httpRepo = new MavenHttpRepository(server, new MavenFileRepository(file("maven-repo")))

    @Override
    void setup() {
        requireOwnGradleUserHomeDir()
    }

    def "uses cached artifacts from previous Gradle version when no sha1 header"() {
        given:
        def projectB = httpRepo.module('org.name', 'projectB', '1.0').publish()
        server.sendSha1Header = false
        server.start()
        buildFile << """
repositories {
    maven { url '${httpRepo.uri}' }
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
        projectB.allowAll()

        and:
        version previous withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        def snapshot = file('libs/projectB-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        projectB.pom.expectHead()
        projectB.pom.sha1.expectGet()
        projectB.artifact.expectHead()
        projectB.artifact.sha1.expectGet()

        and:
        version current withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        file('libs/projectB-1.0.jar').assertContentsHaveNotChangedSince(snapshot)
    }

    def "uses cached artifacts from previous Gradle version with sha1 header"() {
        given:
        def projectB = httpRepo.module('org.name', 'projectB', '1.0').publish()
        server.sendSha1Header = true
        server.start()
        buildFile << """
repositories {
    maven { url '${httpRepo.uri}' }
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
        projectB.allowAll()

        and:
        version previous withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        def snapshot = file('libs/projectB-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        projectB.pom.expectHead()
        projectB.artifact.expectHead()

        and:
        version current withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        file('libs/projectB-1.0.jar').assertContentsHaveNotChangedSince(snapshot)
    }

    def "uses cached artifacts from previous Gradle version that match dynamic version"() {
        given:
        def projectB = httpRepo.module('org.name', 'projectB', '1.1').publish()
        server.start()

        buildFile << """
repositories {
    maven { url '${httpRepo.uri}' }
}
configurations { compile }
dependencies {
    compile 'org.name:projectB:[1.0,2.0]'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
        and:
        def userHome = file('user-home')

        when:
        httpRepo.getModuleMetaData("org.name", "projectB").expectGet()
        projectB.allowAll()

        and:
        version previous withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.1.jar')
        def snapshot = file('libs/projectB-1.1.jar').snapshot()

        when:
        server.resetExpectations()
        httpRepo.getModuleMetaData("org.name", "projectB").expectGet()
        projectB.pom.expectHead()
        projectB.pom.sha1.expectGet()
        projectB.artifact.expectHead()
        projectB.artifact.sha1.expectGet()

        and:
        version current withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.1.jar')
        file('libs/projectB-1.1.jar').assertContentsHaveNotChangedSince(snapshot)
    }
}
