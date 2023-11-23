/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r40

import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

@IntegrationTestTimeout(300)
@TargetGradleVersion(">=4.0")
class BuildProgressCrossVersionSpec extends AbstractProgressCrossVersionSpec {

    private RepositoryHttpServer server

    def setup() {
        server = new RepositoryHttpServer(temporaryFolder, targetDist.version.version)
        server.before()
    }

    def cleanup() {
        server.after()
    }

    def "generates events for applied init-scripts"() {
        given:
        def initScript1 = file('init1.gradle')
        def initScript2 = file('init2.gradle')
        [initScript1, initScript2].each { it << '' }

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .withArguments('--init-script', initScript1.toString(), '--init-script', initScript2.toString())
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        events.operation('Run init scripts').with {
            it.child applyInitScript(initScript1)
            it.child applyInitScript(initScript2)
        }
    }

    @TargetGradleVersion(">=4.0 <4.7")
    @Issue("gradle/gradle#1641")
    def "generates download events during maven publish"() {
        given:
        toolingApi.requireIsolatedUserHome()

        def module = mavenHttpRepo.module('group', 'publish', '1')
        module.withoutExtraChecksums()

        // module is published
        module.publish()

        // module will be published a second time via 'maven-publish'
        module.artifact.expectPublish(false)
        module.pom.expectPublish(false)
        module.rootMetaData.expectGet()
        module.rootMetaData.sha1.expectGet()
        module.rootMetaData.expectGet()
        module.rootMetaData.sha1.expectGet()
        module.rootMetaData.expectPublish(false)

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            version = '1'
            group = 'group'

            publishing {
                repositories {
                    maven { url "${mavenHttpRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        def events = ProgressEvents.create()

        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('publish')
                    .addProgressListener(events).run()
        }

        then:
        println events.describeOperationsTree()
        def roots = events.operations.findAll { it.parent == null }
        roots.any { it.descriptor.name == 'Run build' }

        def orphans = roots.findAll { it.descriptor.name != 'Run build' }
        orphans.size() == 4
        orphans.findAll { it.descriptor.name.startsWith('Unmanaged thread operation #') } == orphans
        orphans[0].child "Download ${module.rootMetaData.uri}"
        orphans[1].child "Download ${module.rootMetaData.sha1.uri}"
        orphans[2].child "Download ${module.rootMetaData.uri}"
        orphans[3].child "Download ${module.rootMetaData.sha1.uri}"
    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    MavenFileRepository getMavenRepo(String name = "repo") {
        return new MavenFileRepository(file(name))
    }
}
