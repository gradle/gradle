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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.HttpResource
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import spock.lang.Unroll

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

class DependencyResolveTimeoutIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private static final String GROUP_ID = 'group'
    private static final String VERSION = '1.0'
    TestFile downloadedLibsDir
    MavenHttpModule moduleA

    def setup() {
        moduleA = publishMavenModule(mavenHttpRepo, 'a')
        downloadedLibsDir = file('build/libs')
        executer.withArgument("-D${SOCKET_TIMEOUT_SYSTEM_PROPERTY}=1000")
    }

    void blockingForProtocol(String protocol, HttpResource... resources) {
        if (protocol == 'http') {
            resources.each { it.expectGetBlocking() }
        } else if (protocol == 'https') {
            // https://issues.apache.org/jira/browse/HTTPCLIENT-1478
            def keyStore = TestKeyStore.init(temporaryFolder.file('ssl-keystore'))
            keyStore.enableSslWithServerCert(server)
            keyStore.configureServerCert(executer)
            server.expectSslHandshakeBlocking()
        } else {
            assert false: "Unsupported protocol: ${protocol}"
        }
    }

    @Unroll
    def "fails single build script dependency resolution if #protocol connection exceeds timeout"() {
        given:
        blockingForProtocol(protocol, moduleA.pom)
        buildFile << """
            buildscript {
                ${mavenRepository(mavenHttpRepo)}

                dependencies {
                    classpath '${mavenModuleCoordinates(moduleA)}'
                }
            }
        """

        when:
        fails('help')

        then:
        assertDependencyReadTimeout(moduleA)

        where:
        protocol << ['http', 'https']
    }

    @Unroll
    def "fails single application dependency resolution if #protocol connection exceeds timeout"() {
        given:
        blockingForProtocol(protocol, moduleA.pom)
        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        fails('resolve')

        then:
        assertDependencyReadTimeout(moduleA)
        !downloadedLibsDir.isDirectory()

        where:
        protocol << ['http', 'https']
    }

    @Unroll
    def "fails concurrent application dependency resolution if #protocol connection exceeds timeout"() {
        given:
        MavenHttpModule moduleB = publishMavenModule(mavenHttpRepo, 'b')
        MavenHttpModule moduleC = publishMavenModule(mavenHttpRepo, 'c')
        blockingForProtocol(protocol, moduleA.pom, moduleB.pom, moduleC.pom)

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA, moduleB, moduleC)}
            ${configSyncTask()}
        """

        when:
        fails('resolve', '--max-workers=3')

        then:
        assertDependencyReadTimeout(moduleA)
        assertDependencyReadTimeout(moduleB)
        assertDependencyReadTimeout(moduleC)
        !downloadedLibsDir.isDirectory()
        where:
        protocol << ['http', 'https']
    }

    private String mavenRepository(MavenRepository repo) {
        """
            repositories {
                maven { url "${repo.uri}"}
            }
        """
    }

    private String customConfigDependencyAssignment(MavenHttpModule... modules) {
        """
            configurations {
                deps
            }
            
            dependencies {
                deps ${modules.collect { "'${mavenModuleCoordinates(it)}'" }.join(', ')}
            }
        """
    }

    private String configSyncTask() {
        """
            task resolve(type: Sync) {
                from configurations.deps
                into "\$buildDir/libs"
            }
        """
    }

    private void assertDependencyReadTimeout(MavenModule module) {
        failure.error.contains("""> Could not resolve ${mavenModuleCoordinates(module)}.
   > Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.
      > Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.
         > Read timed out""")
    }

    private String mavenModuleCoordinates(MavenHttpModule module) {
        "$module.groupId:$module.artifactId:$module.version"
    }

    private String mavenModuleRepositoryPath(MavenHttpModule module) {
        "$module.groupId/$module.artifactId/$module.artifactId-$module.version"
    }

    private MavenHttpModule publishMavenModule(MavenHttpRepository mavenHttpRepo, String artifactId) {
        mavenHttpRepo.module(GROUP_ID, artifactId, VERSION).publish()
    }
}
