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

package org.gradle.integtests.resolve.caching

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.maven.MavenHttpModule

class RecoverFromBrokenResolutionIntegrationTest extends AbstractDependencyResolutionTest {

    def repo
    def module

    def setup() {
        server.start()

        repo = mavenHttpRepo("repo")

        buildFile << """

                       configurations {
                           compile
                       }

                       configurations.all {
                           resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
                       }

                       dependencies {
                           compile 'group:projectA:1.0-SNAPSHOT'
                       }

                       task retrieve(type: Sync) {
                           into 'libs'
                           from configurations.compile
                       }
                       """
    }

    def "can run offline mode after hitting broken repo url"() {
        given:
        noAuthorizationRepo()

        modulePublished()
        when:
        moduleAvailableViaHttp()
        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        server.addBroken("/")
        then:
        fails 'retrieve'

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
    }


    def "can run offline mode after connection problem with repo url"() {
        given:
        buildFile << """
                repositories {
                    maven {
                        name 'repo'
                        url '${repo.uri}'
                    }
                }
                """

        modulePublished()

        when:
        moduleAvailableViaHttp()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        server.stop()
        then:
        fails 'retrieve'

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
    }

    def "can run offline mode after authentication fails on remote repo"() {
        given:
        authorizationRepo()
        modulePublished()

        when:
        server.resetExpectations()
        server.expectGet('/repo/group/projectA/1.0-SNAPSHOT/maven-metadata.xml', 'username', 'password', module.metaDataFile)
        server.expectGet("/repo/group/projectA/1.0-SNAPSHOT/${module.pomFile.name}", 'username', 'password', module.pomFile)
        server.expectGet("/repo/group/projectA/1.0-SNAPSHOT/${module.artifactFile.name}", 'username', 'password', module.artifactFile)

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        server.allowGetOrHead('/repo/group/projectA/1.0-SNAPSHOT/maven-metadata.xml', 'bad_username', 'password', module.metaDataFile)

        then:
        fails 'retrieve'

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
    }


    def moduleAvailableViaHttp() {
        module.expectMetaDataGet()
        module.expectPomGet()
        module.getArtifact().expectGet()
    }

    private MavenHttpModule modulePublished() {
        module = repo.module("group", "projectA", "1.0-SNAPSHOT")
        module.publish()
        module
    }

    private noAuthorizationRepo() {
        buildFile << """
            repositories {
                maven {
                    name 'repo'
                    url '${repo.uri}'
                }
            }"""
    }

    private authorizationRepo() {
        buildFile << """
            repositories {
                maven {
                    url '${repo.uri}'
                    credentials {
                        password 'password'
                        username 'username'
                    }
                }
            }"""
    }

}
