/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

class IvyPublishChangingUrlIntegTest extends AbstractIvyPublishIntegTest {

    @Rule HttpServer server

    String moduleName = "publish"
    String org = "org.gradle"
    String rev = "2"

    IvyFileRepository repo1 = new IvyFileRepository(file("repo1"))
    IvyModule repo1Module = repo1.module(org, moduleName, rev)

    IvyFileRepository repo2 = new IvyFileRepository(file("repo2"))
    IvyModule repo2Module = repo2.module(org, moduleName, rev)

    // This documents observable behavior from the Nexus plugin
    @ToBeFixedForConfigurationCache(because = "changes to IvyArtifactRepository.getUrl are lost")
    def "can change URL to repository from provider"() {
        given:
        settingsFile << "rootProject.name = '${moduleName}'"
        buildFile << """
            plugins {
                id 'java'
                id 'ivy-publish'
            }

            group = '${org}'
            version = '${rev}'

            // This build service manages the mapping between repository id -> repository URL
            // In this simplified case, there's just one URL. The Nexus plugin does something similar
            abstract class StagingRepositoryDescriptorRegistryBuildService implements BuildService<org.gradle.api.services.BuildServiceParameters.None> {
                Object url = "${repo1.uri}"
            }

            def registry = rootProject.gradle.sharedServices.registerIfAbsent("urlRegistry", StagingRepositoryDescriptorRegistryBuildService) {
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
                repositories {
                    ivy {
                        name = "testRepo"
                        url = registry.map { it.url }
                    }
                }
            }

            abstract class InitializeRepository extends DefaultTask {
                @Internal
                abstract Property<StagingRepositoryDescriptorRegistryBuildService> getRepositoryRegistry()

                @TaskAction
                void initializeRepository() {
                    // Imagine this is hitting some webservice to calculate a new URL
                    repositoryRegistry.get().url = "${repo2.uri}"
                }
            }
            task initializeRepository(type: InitializeRepository) {
                repositoryRegistry.set(registry)
            }

            initializeRepository.dependsOn generateMetadataFileForIvyPublication, generateDescriptorFileForIvyPublication
            publishIvyPublicationToTestRepoRepository.dependsOn initializeRepository
        """

        when:
        succeeds "publish"

        then:
        executed(":publishIvyPublicationToTestRepoRepository")

        and:
        repo1Module.ivyFile.assertDoesNotExist()
        repo1Module.jarFile.assertDoesNotExist()
        repo2Module.ivyFile.assertExists()
        repo2Module.jarFile.assertExists()
    }

}
