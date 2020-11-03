/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.resource.transport.http

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.internal.resource.transport.http.fixtures.MavenWebdavRepository
import org.gradle.internal.resource.transport.http.fixtures.WebdavServer
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.junit.Rule

class MavenPublishWebdavIntegrationTest extends AbstractMavenPublishIntegTest {

    @Rule
    public WebdavServer server = new WebdavServer(temporaryFolder, [ "username": "password" ])

    def setup() {
        TestKeyStore keyStore = TestKeyStore.init(file("keystore"))
        keyStore.enableSslWithServerCert(server)
        keyStore.configureServerCert(executer)

        server.start()

        settingsFile << 'rootProject.name = "publishWebdavTest"'
    }

    def "can publish to a webdav Maven repository"() {
        given:
        def mavenRepo = new MavenWebdavRepository(server)
        buildFile << publicationBuild(mavenRepo.uri, """
            authentication {
                basic(BasicAuthentication)
            }
            credentials(PasswordCredentials) {
                username "username"
                password "password"
            }
            """)

        when:
        def module = mavenRepo.module('org.gradle.test', 'publishWebdavTest', '1.0').withModuleMetadata()

        succeeds 'publish'

        then:
        module.assertPublishedAsJavaModule()
        module.parsedPom.scopes.isEmpty()
    }

    private static String publicationBuild(URI repoUrl, String authentication) {
        return """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven {
                        url "${repoUrl}"
                        ${authentication}
                    }
                }

                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """
    }
}
