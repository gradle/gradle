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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.maven.MavenHttpModule
import org.hamcrest.Matchers

class RecoverFromBrokenResolutionIntegrationTest extends AbstractDependencyResolutionTest {

    def repo
    def module

    def setup() {
        server.start()
    }

    private void buildFileWithSnapshotDependency() {
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
        buildFileWithSnapshotDependency()
        and:
        noAuthorizationRepo()

        publishedMavenModule()
        when:
        moduleAvailableViaHttp()
        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        server.addBroken("/")
        then:
        fails 'retrieve'

        and:
        //TODO should expose the failed task in the error message like
        //failure.assertHasDescription('Execution failed for task \':retrieve\'.')
        //failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertHasDescription('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertThatCause(Matchers.containsString("Received status code 500 from server: broken"))


        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifact.file)
    }

    def "can run offline mode after connection problem with repo url using unique snapshot version"() {
        given:
        buildFileWithSnapshotDependency()
        noAuthorizationRepo()
        publishedMavenModule()

        when:
        moduleAvailableViaHttp()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        int port = server.port
        server.stop()
        then:
        fails 'retrieve'

        and:
        //TODO should expose the failed task in the error message like
        //failure.assertHasDescription('Execution failed for task \':retrieve\'.')
        //failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertHasDescription('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertThatCause(Matchers.containsString("Connection to http://localhost:${port} refused"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifact.file)
    }

    def "can run offline mode after connection problem with repo url using non unique snapshot version"() {
        given:
        buildFileWithSnapshotDependency()
        noAuthorizationRepo()
        publishedMavenModule(true)

        when:
        moduleAvailableViaHttpWithoutMetaData()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        int port = server.port
        server.stop()
        then:
        fails 'retrieve'

        and:
        //TODO should expose the failed task in the error message like
        //failure.assertHasDescription('Execution failed for task \':retrieve\'.')
        //failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertHasDescription('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertThatCause(Matchers.containsString("Connection to http://localhost:${port} refused"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifact.file)
    }

    def "can run offline mode after authentication fails on remote repo"() {
        given:
        buildFileWithSnapshotDependency()
        and:
        authorizationRepo()
        when:
        publishedMavenModule()
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

        and:
        //TODO should expose the failed task in the error message like
        //failure.assertHasDescription('Execution failed for task \':retrieve\'.')
        //failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertHasDescription('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertThatCause(Matchers.containsString("Received status code 401 from server: Unauthorized"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifact.file)
    }

    def "can run offline mode after connection problem with repo when using ivy changing modules"() {
        given:
        def ivyRepo = ivyHttpRepo("ivyRepo")
        def ivyModule = ivyRepo.module("group", "projectA", "1.0")
        ivyModule.publish()
        and:
        buildFile.text = """
                  repositories {
                       ivy {
                           name 'repo'
                           url '${ivyRepo.uri}'
                       }
                  }
                  configurations {
                      compile
                  }

                  configurations.all {
                      resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
                  }

                  dependencies {
                      compile group:'group', name:'projectA', version:'1.0', changing:true
                  }

                  task retrieve(type: Sync) {
                      into 'libs'
                      from configurations.compile
                  }"""
        when:
        ivyModule.expectIvyGet()
        ivyModule.expectJarGet()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        int port = server.port
        server.stop()
        then:
        fails 'retrieve'

        and:
        //TODO should expose the failed task in the error message like
        //failure.assertHasDescription('Execution failed for task \':retrieve\'.')
        //failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertHasDescription('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertThatCause(Matchers.containsString("Connection to http://localhost:${port} refused"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0.jar').assertIsCopyOf(ivyModule.jarFile)
    }

    def moduleAvailableViaHttp() {
        module.metaData.expectGet()
        module.pom.expectGet()
        module.getArtifact().expectGet()
    }

    def moduleAvailableViaHttpWithoutMetaData() {
        module.metaData.expectGetMissing()
        module.pom.expectGet()
        module.getArtifact().expectGet()
    }


    private MavenHttpModule publishedMavenModule(withNonUniqueVersion = false) {
        module = repo.module("group", "projectA", "1.0-SNAPSHOT")
        if (withNonUniqueVersion) {
            module.withNonUniqueSnapshots()
        }
        module.publish()
        module
    }

    private noAuthorizationRepo() {
        repo = mavenHttpRepo("repo")

        buildFile << """
        repositories {
            maven {
                name 'repo'
                url '${repo.uri}'
            }
        } """
    }

    private authorizationRepo() {
        repo = mavenHttpRepo("repo")
        buildFile << """
        repositories {
            maven {
                url '${repo.uri}'
                credentials {
                    password 'password'
                    username 'username'
                }
            }
        }
        """
    }

}
