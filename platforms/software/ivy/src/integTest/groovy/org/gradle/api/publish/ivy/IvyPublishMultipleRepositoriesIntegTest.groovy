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

package org.gradle.api.publish.ivy

import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

class IvyPublishMultipleRepositoriesIntegTest extends AbstractIvyPublishIntegTest {

    @Rule HttpServer server

    String moduleName = "publish"
    String org = "org.gradle"
    String rev = "2"

    IvyFileRepository repo1 = new IvyFileRepository(file("repo1"))
    IvyModule repo1Module = repo1.module(org, moduleName, rev)

    IvyFileRepository repo2 = new IvyFileRepository(file("repo2"))
    IvyModule repo2Module = repo2.module(org, moduleName, rev)

    def "can publish to different repositories"() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        descriptor.description {
                            text = 'test module'
                        }
                    }
                }
                repositories {
                    ivy {
                        url = "${repo1.uri}"
                    }
                    ivy {
                        name = "repo2"
                        url = "${repo2.uri}"
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        executed(":publishIvyPublicationToIvyRepository")
        executed(":publishIvyPublicationToRepo2Repository")

        and:
        repo1Module.ivyFile.exists()
        repo1Module.jarFile.exists()
        repo2Module.ivyFile.exists()
        repo2Module.jarFile.exists()

        and: // Modification applied to both
        repo1Module.parsedIvy.description.text() == "test module"
        repo2Module.parsedIvy.description.text() == "test module"
    }

}
