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
import org.gradle.integtests.fixtures.MavenHttpRepository
import org.gradle.integtests.fixtures.TargetVersions
import org.junit.Rule
// TODO:DAZ Support for milestone-3 does not include POM reuse. We should probably ditch milestone-3 support after 1.0.
@TargetVersions('1.0-milestone-3')
class M3CacheReuseCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    final MavenHttpRepository remoteRepo = new MavenHttpRepository(server, mavenRepo)

    def "uses cached artifacts from previous Gradle version"() {
        given:
        def projectB = remoteRepo.module('org.name', 'projectB').publish()

        server.start()
        buildFile << """
repositories {
    mavenRepo(urls: ['${remoteRepo.uri}'])
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
        version previous withUserHomeDir userHome withTasks 'retrieve' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        def snapshot = file('libs/projectB-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        projectB.expectPomGet()
        projectB.expectArtifactHead()
        projectB.expectArtifactSha1Get()

        and:
        version current withUserHomeDir userHome withTasks 'retrieve' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        file('libs/projectB-1.0.jar').assertContentsHaveNotChangedSince(snapshot)
    }
}
