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

package org.gradle.configurationcache

import org.gradle.api.credentials.PasswordCredentials
import org.gradle.internal.credentials.DefaultPasswordCredentials
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.util.internal.GUtil
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue

import static org.gradle.util.internal.GFileUtils.deleteDirectory
import static org.gradle.util.internal.GFileUtils.listFiles

class ConfigurationCacheMavenPublishIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Rule
    public final HttpServer server = new HttpServer()

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

            def testAttributes = project.services.get(ImmutableAttributesFactory)
                 .mutable()
                 .attribute(Attribute.of('foo', String), 'value')
        """
    }

    def "can publish maven publication metadata to remote repository"() {
        def username = "someuser"
        def password = "somepassword"
        def projectConfig = configureProject(username, password, "mavenRepo", false)
        def configurationCache = newConfigurationCacheFixture()
        def metadataFile = file('build/publications/maven/module.json')

        expect:
        !GUtil.isSecureUrl(server.uri)

        when:
        prepareMavenHttpRepository(projectConfig.remoteRepo, new DefaultPasswordCredentials(username, password))
        configurationCacheRun(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertStateStored()
        metadataFile.exists()

        when:
        def storeTimeRepo = mavenRepoFiles()
        def storeTimeMetadata = metadataFile.text
        metadataFile.delete()
        deleteDirectory(mavenRepo.rootDir)

        prepareMavenHttpRepository(projectConfig.remoteRepo, new DefaultPasswordCredentials(username, password))
        configurationCacheRun(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertStateLoaded()
        def loadTimeRepo = mavenRepoFiles()
        storeTimeRepo == loadTimeRepo
        def loadTimeMetadata = metadataFile.text
        storeTimeMetadata == loadTimeMetadata
    }

    def "can publish maven publication metadata to non-authenticating remote repository"() {
        with(server) {
            // or else insecure protocol enforcement is skipped
            useHostname()
            start()
        }
        def remoteRepo = new MavenHttpRepository(server, mavenRepo)

        def repositoryName = "testrepo"
        settingsFile "rootProject.name = 'root'"
        buildFile buildFileConfiguration("""
            repositories {
                maven {
                    name "${repositoryName}"
                    url "${remoteRepo.uri}"
                    allowInsecureProtocol true
                    // no credentials
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()
        def metadataFile = file('build/publications/maven/module.json')
        def tasks = [
            "generateMetadataFileForMavenPublication",
            "generatePomFileForMavenPublication",
            "publishMavenPublicationTo${repositoryName}Repository",
            "publishAllPublicationsTo${repositoryName}Repository"
        ]

        expect:
        !GUtil.isSecureUrl(server.uri)

        when:
        prepareMavenHttpRepository(remoteRepo, null)
        configurationCacheRun(*tasks)
        server.resetExpectations()

        then:
        configurationCache.assertStateStored()
        metadataFile.exists()

        when:
        def storeTimeRepo = mavenRepoFiles()
        def storeTimeMetadata = metadataFile.text
        metadataFile.delete()
        deleteDirectory(mavenRepo.rootDir)

        prepareMavenHttpRepository(remoteRepo, null)
        configurationCacheRun(*tasks)
        server.resetExpectations()

        then:
        configurationCache.assertStateLoaded()
        def loadTimeRepo = mavenRepoFiles()
        storeTimeRepo == loadTimeRepo
        def loadTimeMetadata = metadataFile.text
        storeTimeMetadata == loadTimeMetadata
    }

    @Issue("https://github.com/gradle/gradle/issues/22618")
    @Ignore("This relies on being able to reliably detect unsafe credentials")
    def "cannot use unsafe credentials provider with configuration cache"() {
        def username = "someuser"
        def password = "somepassword"
        def repositoryName = "testMavenRepo"
        def projectConfig = configureProject(username, password, repositoryName, true)
        def configurationCache = newConfigurationCacheFixture()

        when:
        prepareMavenHttpRepository(projectConfig.remoteRepo, new DefaultPasswordCredentials(username, password))
        configurationCacheFails(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertStateStored(false)
        outputContains("Configuration cache entry discarded")
        failure.assertHasFailures(1)
        failure.assertHasDescription("Configuration cache problems found in this build")
        failure.assertHasCause("Credential values found in configuration for: repository testMavenRepo")
    }

    @Issue("https://github.com/gradle/gradle/issues/22618")
    @Ignore("This relies on being able to reliably detect unsafe credentials")
    def "cannot use identity-incompatible repository name credentials provider with configuration cache"() {
        def username = "someuser"
        def password = "somepassword"
        def repositoryName = "repo-with-invalid-identity-name"
        def projectConfig = configureProject(username, password, repositoryName, true)
        def configurationCache = newConfigurationCacheFixture()

        when:
        prepareMavenHttpRepository(projectConfig.remoteRepo, new DefaultPasswordCredentials(username, password))
        configurationCacheFails(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertStateStored(false)
        outputContains("Configuration cache entry discarded")
        failure.assertHasFailures(1)
        failure.assertHasDescription("Configuration cache problems found in this build")
        failure.assertHasCause("Credential values found in configuration for: repository repo-with-invalid-identity-name")
    }

    def "can publish maven publication metadata to local repository"() {
        settingsFile "rootProject.name = 'root'"
        buildFile buildFileConfiguration("""
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()
        def metadataFile = file('build/publications/maven/module.json')
        def tasks = [
            'generateMetadataFileForMavenPublication',
            'generatePomFileForMavenPublication',
            'publishMavenPublicationToMavenRepository',
            'publishAllPublicationsToMavenRepository'
        ]

        when:
        configurationCacheRun(*tasks)

        then:
        configurationCache.assertStateStored()
        metadataFile.exists()

        when:
        def storeTimeRepo = mavenRepoFiles()
        def storeTimeMetadata = metadataFile.text
        metadataFile.delete()
        deleteDirectory(mavenRepo.rootDir)
        configurationCacheRun(*tasks)

        then:
        configurationCache.assertStateLoaded()
        def loadTimeRepo = mavenRepoFiles()
        storeTimeRepo == loadTimeRepo
        def loadTimeMetadata = metadataFile.text
        storeTimeMetadata == loadTimeMetadata
    }

    private String buildFileConfiguration(String repositoriesBlock) {
        """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def mainComponent = new TestComponent()
            mainComponent.usages.add(
                new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'),
                    dependencies: configurations.implementation.allDependencies.withType(ModuleDependency),
                    dependencyConstraints: configurations.implementation.allDependencyConstraints,
                    attributes: testAttributes
                )
            )

            dependencies {
                implementation("org:foo:1.0") {
                   because 'version 1.0 is tested'
                }
                constraints {
                    implementation("org:bar:2.0") {
                        because 'because 2.0 is cool'
                    }
                }
            }

            publishing {
                $repositoriesBlock
                publications {
                    maven(MavenPublication) {
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
        def remoteRepo = new MavenHttpRepository(server, mavenRepo)

        settingsFile "rootProject.name = 'root'"

        def credentialsBlock
        if (inlinedCredentials) {
            credentialsBlock = """
                credentials {
                    username '${username}'
                    password '${password}'
                }
            """
        } else {
            credentialsBlock = "credentials(PasswordCredentials)"
            configureRepositoryCredentials(username, password, repositoryName)
        }
        buildFile buildFileConfiguration("""
            repositories {
                maven {
                    name "${repositoryName}"
                    url "${remoteRepo.uri}"
                    allowInsecureProtocol true
                    ${credentialsBlock}
                }
            }
        """)

        def tasks = [
            "generateMetadataFileForMavenPublication",
            "generatePomFileForMavenPublication",
            "publishMavenPublicationTo${repositoryName}Repository",
            "publishAllPublicationsTo${repositoryName}Repository"
        ]
        return new ProjectConfiguration([tasks: tasks, remoteRepo: remoteRepo])
    }

    class ProjectConfiguration {
        List<String> tasks
        MavenHttpRepository remoteRepo
    }

    private void prepareMavenHttpRepository(MavenHttpRepository repository, PasswordCredentials credentials) {
        def rootModule = repository.module("group", "root")
        rootModule.pom.expectPublish(true, credentials)
        rootModule.moduleMetadata.expectPublish(true, credentials)
        rootModule.rootMetaData.expectGetMissing(credentials)
        rootModule.rootMetaData.expectPublish(true, credentials)
    }

    private Map<File, String> mavenRepoFiles() {
        listFiles(mavenRepo.rootDir, null, true)
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
