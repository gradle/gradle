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

package org.gradle.internal.cc.impl

import org.gradle.api.credentials.PasswordCredentials
import org.gradle.test.fixtures.ivy.IvyRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.util.TestCredentialUtil
import org.gradle.util.internal.GUtil
import org.junit.Rule

import static org.gradle.test.fixtures.server.http.HttpServer.SupportedHash.SHA1
import static org.gradle.test.fixtures.server.http.HttpServer.SupportedHash.SHA256
import static org.gradle.test.fixtures.server.http.HttpServer.SupportedHash.SHA512
import static org.gradle.util.internal.GFileUtils.deleteDirectory
import static org.gradle.util.internal.GFileUtils.listFiles

class ConfigurationCacheIvyPublishIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Rule
    public final HttpServer server = new HttpServer().tap {
        supportedHashes = EnumSet.of(SHA1, SHA256, SHA512)
    }

    def setup() {
        buildFile """
            // TODO - use public APIs when available
            import org.gradle.api.internal.attributes.*
            import org.gradle.api.internal.component.*
            class TestComponent implements SoftwareComponentInternal, ComponentWithVariants {
                String name
                Set usages = []
                Set variants = []
            }

            class TestUsage implements UsageContext {
                String name
                Usage usage
                Set dependencies = []
                Set dependencyConstraints = []
                Set artifacts = []
                Set capabilities = []
                Set globalExcludes = []
                AttributeContainer attributes = ImmutableAttributes.EMPTY
            }

            class TestVariant implements SoftwareComponentInternal {
                String name
                Set usages = []
            }

            class TestCapability implements Capability {
                String group
                String name
                String version
            }

            allprojects {
                configurations { implementation }
            }

            def testAttributes = project.services.get(AttributesFactory)
                 .mutable()
                 .attribute(Attribute.of('foo', String), 'value')
        """
    }

    def "can execute generateDescriptorFile"() {
        def configurationCache = newConfigurationCacheFixture()
        buildConfigurationWithIvyRepository(ivyRepo, "ivyRepo","")

        when:
        configurationCacheRun("generateDescriptorFileForIvyPublication")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("generateDescriptorFileForIvyPublication")

        then:
        configurationCache.assertStateLoaded()
    }

    def "can execute generateMetadataFileForIvyPublication"() {
        def configurationCache = newConfigurationCacheFixture()
        buildConfigurationWithIvyRepository(ivyRepo, "ivyRepo","")

        when:
        configurationCacheRun("generateMetadataFileForIvyPublication")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("generateMetadataFileForIvyPublication")

        then:
        configurationCache.assertStateLoaded()
    }

    def "can publish ivy publication metadata to remote repository"() {
        def username = "someuser"
        def password = "somepassword"
        def projectConfig = configureProject(username, password, "ivyRepo", false)
        def configurationCache = newConfigurationCacheFixture()
        def metadataFile = file('build/publications/ivy/module.json')

        expect:
        !GUtil.isSecureUrl(server.uri)

        when:
        prepareIvyHttpRepository(projectConfig.remoteRepo, TestCredentialUtil.defaultPasswordCredentials(username, password))
        configurationCacheRun(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertStateStored()
        metadataFile.exists()

        when:
        def storeTimeRepo = ivyRepoFiles()
        def storeTimeMetadata = metadataFile.text
        metadataFile.delete()
        deleteDirectory(ivyRepo.rootDir)

        prepareIvyHttpRepository(projectConfig.remoteRepo, TestCredentialUtil.defaultPasswordCredentials(username, password))
        configurationCacheRun(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertStateLoaded()
        //TODO we may need to exclude some of the files as we do for Maven
        //def loadTimeRepo = ivyRepoFiles()
        //storeTimeRepo == loadTimeRepo
        def loadTimeMetadata = metadataFile.text
        storeTimeMetadata == loadTimeMetadata
    }

    def "can execute publishAllPublicationsToIvyRepoRepository"() {
        def username = "someuser"
        def password = "somepassword"
        def projectConfig = configureProject(username, password, "ivyRepo", false)
        def configurationCache = newConfigurationCacheFixture()

        expect:
        !GUtil.isSecureUrl(server.uri)

        when:
        prepareIvyHttpRepository(projectConfig.remoteRepo, TestCredentialUtil.defaultPasswordCredentials(username, password))
        configurationCacheRun("publishAllPublicationsToIvyRepoRepository")
        server.resetExpectations()

        then:
        configurationCache.assertStateStored()

        when:
        def storeTimeRepo = ivyRepoFiles()
        deleteDirectory(ivyRepo.rootDir)

        prepareIvyHttpRepository(projectConfig.remoteRepo, TestCredentialUtil.defaultPasswordCredentials(username, password))
        configurationCacheRun("publishAllPublicationsToIvyRepoRepository")
        server.resetExpectations()

        then:
        configurationCache.assertStateLoaded()
    }

    private void buildFileConfiguration(String repositoriesBlock) {
        buildFile """
            apply plugin: 'ivy-publish'

            group = 'group'
            version = '1.0'

            def mainComponent = new TestComponent()
            mainComponent.usages.add(
                new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'),
                    dependencies: configurations.implementation.allDependencies.withType(ModuleDependency),
                    attributes: testAttributes
                )
            )

            dependencies {
                implementation("org:foo:1.0") {
                   because 'version 1.0 is tested'
                }
            }

            publishing {
                $repositoriesBlock
                publications {
                    ivy(IvyPublication) {
                        from mainComponent
                    }
                }
            }
        """
    }

    private ProjectConfiguration configureProject(String username, String password, String repositoryName, boolean inlinedCredentials) {
        assert !inlinedCredentials : "Inlined credentials are not supported with the configuration cache"
        with (server) {
            requireAuthentication(username, password)
            // or else insecure protocol enforcement is skipped
            useHostname()
            start()
        }
        def remoteRepo = new IvyHttpRepository(server, ivyRepo)

        settingsFile "rootProject.name = 'root'"

        def credentialsBlock
        if (inlinedCredentials) {
            credentialsBlock = """
                credentials {
                    username = '${username}'
                    password = '${password}'
                }
            """
        } else {
            credentialsBlock = "credentials(PasswordCredentials)"
            configureRepositoryCredentials(username, password, repositoryName)
        }
        buildFileConfiguration("""
            repositories {
                ivy {
                    name = "${repositoryName}"
                    url = "${remoteRepo.uri}"
                    allowInsecureProtocol = true
                    ${credentialsBlock}
                }
            }
        """)

        def tasks = [
            "generateMetadataFileForIvyPublication",
            "generateDescriptorFileForIvyPublication",
            "publishIvyPublicationTo${repositoryName}Repository",
            "publishAllPublicationsTo${repositoryName}Repository"
        ]
        return new ProjectConfiguration([tasks: tasks, remoteRepo: remoteRepo])
    }

    String buildConfigurationWithIvyRepository(IvyRepository repository, String repositoryName, String credentialsBlock) {
        buildFileConfiguration("""
            repositories {
                ivy {
                    name = "${repositoryName}"
                    url = "${repository.uri}"
                    ${credentialsBlock}
                }
            }
        """)
    }

    class ProjectConfiguration {
        List<String> tasks
        IvyHttpRepository remoteRepo
    }

    private void prepareIvyHttpRepository(IvyHttpRepository repository, PasswordCredentials credentials) {
        def rootModule = repository.module("group", "root")
        rootModule.ivy.expectPublish(true, credentials)
        rootModule.moduleMetadata.expectPublish(true, credentials)
    }

    private Map<File, String> ivyRepoFiles() {
        listFiles(ivyRepo.rootDir, null, true)
            .collectEntries { File repoFile ->
                [repoFile, textForComparisonOf(repoFile)]
            }
    }

    private String textForComparisonOf(File repositoryFile) {
        def fileName = repositoryFile.name
        if (fileName.startsWith('maven-metadata.xml')) {
            if (fileName == 'maven-metadata.xml') {
                return clearLastUpdatedElementOf(repositoryFile.text)
            }
            // Ignore contents of maven-metadata.xml.sha256, etc, because hashes will most likely
            // change between runs due to <lastUpdated /> differences.
            return ''
        }
        return repositoryFile.text
    }

    private String clearLastUpdatedElementOf(String metadata) {
        metadata.replaceAll(
            "<lastUpdated>\\d+</lastUpdated>",
            "<lastUpdated />"
        )
    }
}
