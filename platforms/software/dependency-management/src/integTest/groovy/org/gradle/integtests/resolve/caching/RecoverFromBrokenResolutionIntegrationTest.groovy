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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.hamcrest.CoreMatchers

import static org.gradle.util.Matchers.matchesRegexp

@IntegrationTestTimeout(120)
class RecoverFromBrokenResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {

    MavenHttpRepository repo
    MavenHttpModule module

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

    @ToBeFixedForConfigurationCache
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
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertThatCause(CoreMatchers.containsString("Received status code 500 from server: broken"))


        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifact.file)
    }

    @ToBeFixedForConfigurationCache
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
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertThatCause(matchesRegexp(".*?Connect to 127.0.0.1:${port} (\\[.*\\])? failed: Connection refused.*"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifact.file)
    }

    @ToBeFixedForConfigurationCache
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
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertThatCause(matchesRegexp(".*?Connect to 127.0.0.1:${port} (\\[.*\\])? failed: Connection refused.*"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifact.file)
    }

    @ToBeFixedForConfigurationCache
    def "can run offline mode after authentication fails on remote repo"() {
        given:
        buildFileWithSnapshotDependency()
        and:
        authorizationRepo()
        when:
        publishedMavenModule()
        server.resetExpectations()
        module.metaData.expectGet('username', 'password')
        module.pom.expectGet('username', 'password')
        module.artifact.expectGet('username', 'password')

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        server.allowGetOrHead('/repo/group/projectA/1.0-SNAPSHOT/maven-metadata.xml', 'bad_username', 'password', module.metaDataFile)

        then:
        fails 'retrieve'

        and:
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertThatCause(CoreMatchers.containsString("Received status code 401 from server: Unauthorized"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifact.file)
    }

    @ToBeFixedForConfigurationCache
    def "can run offline mode after connection problem with repo when using ivy changing modules"() {
        given:
        def ivyRepo = ivyHttpRepo("ivyRepo")
        def ivyModule = ivyRepo.module("group", "projectA", "1.0")
        ivyModule.publish()
        and:
        buildFile.text = """
                  repositories {
                       ivy {
                           name = 'repo'
                           url = "${ivyRepo.uri}"
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
        ivyModule.ivy.expectGet()
        ivyModule.jar.expectGet()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        int port = server.port
        server.stop()
        then:
        fails 'retrieve'

        and:
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertThatCause(matchesRegexp(".*?Connect to 127.0.0.1:${port} (\\[.*\\])? failed: Connection refused.*"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.0.jar').assertIsCopyOf(ivyModule.jarFile)
    }

    @ToBeFixedForConfigurationCache
    def "can run offline mode after connection problem with repo when using ivy dynamic version"() {
        given:
        def ivyRepo = ivyHttpRepo("ivyRepo")
        def ivyModule = ivyRepo.module("group", "projectA", "1.1")
        ivyModule.publish()
        and:
        buildFile << """
                  repositories {
                       ivy { url = "${ivyRepo.uri}" }
                  }
                  configurations {
                      compile
                  }
                  configurations.all {
                      resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
                  }
                  dependencies {
                      compile group:'group', name:'projectA', version:'1.+'
                  }

                  task retrieve(type: Sync) {
                      into 'libs'
                      from configurations.compile
                  }"""
        when:
        ivyModule.repository.directoryList('group', 'projectA').expectGet()
        ivyModule.ivy.expectGet()
        ivyModule.jar.expectGet()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        int port = server.port
        server.stop()
        then:
        fails 'retrieve'

        and:
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Could not list versions using Ivy pattern 'http://127.0.0.1:${port}/ivyRepo/[organisation]/[module]/[revision]/ivy-[revision].xml")
        failure.assertThatCause(matchesRegexp(".*?Connect to 127.0.0.1:${port} (\\[.*\\])? failed: Connection refused.*"))

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
        and:
        file('libs/projectA-1.1.jar').assertIsCopyOf(ivyModule.jarFile)
    }

    def moduleAvailableViaHttp() {
        module.metaData.expectGet()
        module.pom.expectGet()
        module.artifact.expectGet()
    }

    def moduleAvailableViaHttpWithoutMetaData() {
        module.metaData.expectGetMissing()
        module.pom.expectGet()
        module.artifact.expectGet()
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
                name = 'repo'
                url = '${repo.uri}'
            }
        } """
    }

    private authorizationRepo() {
        repo = mavenHttpRepo("repo")
        buildFile << """
        repositories {
            maven {
                url = '${repo.uri}'
                credentials {
                    password = 'password'
                    username = 'username'
                }
            }
        }
        """
    }

}
