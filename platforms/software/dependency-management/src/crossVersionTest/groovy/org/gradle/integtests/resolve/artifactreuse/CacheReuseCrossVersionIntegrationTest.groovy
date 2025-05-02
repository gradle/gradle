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

import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCacheMetadata
import org.gradle.integtests.fixtures.IgnoreVersions
import org.gradle.test.fixtures.file.LeaksFileHandles

@IgnoreVersions({ it.artifactCacheLayoutVersion == DefaultArtifactCacheMetadata.CACHE_LAYOUT_VERSION })
@LeaksFileHandles
class CacheReuseCrossVersionIntegrationTest extends AbstractCacheReuseCrossVersionIntegrationTest {
    def "uses cached artifacts from previous Gradle version when no sha1 header"() {
        given:
        def projectB = mavenHttpRepo.module('org.name', 'projectB', '1.0').publish()
        server.sendSha1Header = false
        buildFile << """
repositories {
    maven { url = '${mavenHttpRepo.uri}' }
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
        def projectB = mavenHttpRepo.module('org.name', 'projectB', '1.0').publish()
        server.sendSha1Header = true
        buildFile << """
repositories {
    maven { url = '${mavenHttpRepo.uri}' }
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
        def projectB = mavenHttpRepo.module('org.name', 'projectB', '1.1').publish()

        buildFile << """
repositories {
    maven { url = '${mavenHttpRepo.uri}' }
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
        mavenHttpRepo.getModuleMetaData("org.name", "projectB").expectGet()
        projectB.allowAll()

        and:
        version previous withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.1.jar')
        def snapshot = file('libs/projectB-1.1.jar').snapshot()

        when:
        server.resetExpectations()
        mavenHttpRepo.getModuleMetaData("org.name", "projectB").expectGet()
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
