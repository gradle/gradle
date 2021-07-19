/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpResource
import org.gradle.test.fixtures.server.http.MavenHttpModule
import spock.lang.Unroll

class DependencyResolutionTimeoutIntegrationTest extends AbstractDependencyUnresolvedModuleIntegrationTest {
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
        assertDependencyMetaDataReadTimeout(moduleA)

        where:
        protocol << ['http', 'https']
    }

    @Unroll
    @ToBeFixedForConfigurationCache
    def "fails single application dependency resolution if #protocol connection exceeds timeout (retries = #maxRetries)"() {
        maxHttpRetries = maxRetries

        given:
        maxHttpRetries.times {
            blockingForProtocol(protocol, moduleA.pom)
        }
        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        fails('resolve')

        then:
        assertDependencyMetaDataReadTimeout(moduleA)
        !downloadedLibsDir.isDirectory()

        where:
        protocol | maxRetries
        'http'   | 1
        'https'  | 1
        'http'   | 2
        'https'  | 2
    }

    @Unroll
    @ToBeFixedForConfigurationCache
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
        assertDependencyMetaDataReadTimeout(moduleA)
        assertDependencyMetaDataReadTimeout(moduleB)
        assertDependencyMetaDataReadTimeout(moduleC)
        !downloadedLibsDir.isDirectory()
        where:
        protocol << ['http', 'https']
    }

    def "prevents using repository in later resolution within the same build on HTTP timeout"() {
        given:
        MavenHttpModule moduleB = publishMavenModule(mavenHttpRepo, 'b')
        MavenHttpModule moduleC = publishMavenModule(mavenHttpRepo, 'c')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}

            configurations {
                first
                second
                third
            }
            dependencies {
                first '${mavenModuleCoordinates(moduleA)}'
                second '${mavenModuleCoordinates(moduleB)}'
                third '${mavenModuleCoordinates(moduleC)}'
            }

            task resolve {
                doLast {
                    def filesA = configurations.first.resolvedConfiguration.lenientConfiguration.files*.name
                    def filesB = configurations.second.resolvedConfiguration.lenientConfiguration.files*.name
                    def filesC = configurations.third.resolvedConfiguration.lenientConfiguration.files*.name
                    println "Resolved: \${filesA} \${filesB} \${filesC}"
                }
            }
        """

        when:
        moduleA.pom.expectGet()
        moduleA.artifact.expectGet()
        moduleB.pom.expectGetBlocking()
        // No attempt made to get moduleC

        succeeds 'resolve'

        then:
        output.contains "Resolved: [a-1.0.jar] [] []"
    }
}
