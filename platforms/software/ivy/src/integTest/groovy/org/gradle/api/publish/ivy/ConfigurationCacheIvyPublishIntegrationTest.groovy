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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ivy.IvyRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.internal.GUtil
import org.junit.Rule

import static org.gradle.test.fixtures.server.http.HttpServer.SupportedHash.SHA1
import static org.gradle.test.fixtures.server.http.HttpServer.SupportedHash.SHA256
import static org.gradle.test.fixtures.server.http.HttpServer.SupportedHash.SHA512
import static org.gradle.util.internal.GFileUtils.deleteDirectory
import static org.gradle.util.internal.GFileUtils.listFiles

@Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly")
class ConfigurationCacheIvyPublishIntegrationTest extends AbstractIntegrationSpec {

    def configurationCache = newConfigurationCacheFixture()

    @Rule
    public final HttpServer server = new HttpServer().tap {
        supportedHashes = EnumSet.of(SHA1, SHA256, SHA512)
    }

    @Override
    void setupExecuter() {
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
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
        buildConfigurationWithIvyRepository(ivyRepo, "ivyRepo","")

        when:
        run("generateDescriptorFileForIvyPublication")

        then:
        configurationCache.assertStateStored()

        when:
        run("generateDescriptorFileForIvyPublication")

        then:
        configurationCache.assertStateLoaded()
    }

    def "can execute generateMetadataFileForIvyPublication"() {
        def configurationCache = newConfigurationCacheFixture()
        buildConfigurationWithIvyRepository(ivyRepo, "ivyRepo","")

        when:
        run("generateMetadataFileForIvyPublication")

        then:
        configurationCache.assertStateStored()

        when:
        run("generateMetadataFileForIvyPublication")

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
        prepareIvyHttpRepository(projectConfig.remoteRepo, new HttpServer.PasswordCredentials(username, password))
        run(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertStateStored()
        metadataFile.exists()

        when:
        def storeTimeRepo = ivyRepoFiles()
        def storeTimeMetadata = metadataFile.text
        metadataFile.delete()
        deleteDirectory(ivyRepo.rootDir)

        prepareIvyHttpRepository(projectConfig.remoteRepo, new HttpServer.PasswordCredentials(username, password))
        run(*(projectConfig.tasks))
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
        prepareIvyHttpRepository(projectConfig.remoteRepo, new HttpServer.PasswordCredentials(username, password))
        run("publishAllPublicationsToIvyRepoRepository")
        server.resetExpectations()

        then:
        configurationCache.assertStateStored()

        when:
        def storeTimeRepo = ivyRepoFiles()
        deleteDirectory(ivyRepo.rootDir)

        prepareIvyHttpRepository(projectConfig.remoteRepo, new HttpServer.PasswordCredentials(username, password))
        run("publishAllPublicationsToIvyRepoRepository")
        server.resetExpectations()

        then:
        configurationCache.assertStateLoaded()
    }

    @spock.lang.Issue("https://github.com/gradle/gradle/issues/29253")
    def "configuration cache state can be stored for ivy publication with artifact from mapped task output"() {
        // Ivy mirror of the maven reproducer for #29253. Same shape: a task-producing
        // DirectoryProperty threaded through `.flatMap { … }.map { … .asFile }` and
        // fed into `IvyPublication.artifact(...)`. Pre-fix: CC store failed with
        // `error writing value of type 'UnionFileCollection'`. Post-fix (Option 1 in
        // classify()): CC store succeeds. Publish task action still fails downstream
        // for the same reason it does on the maven side (SerializableIvyArtifact
        // constructor eagerly resolves the artifact), tracked under umbrella #24329.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'ivy-publish'

            group = 'group'
            version = '1.0'

            abstract class ProduceApk extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void run() {
                    def apk = outputDir.file('app.apk').get().asFile
                    apk.parentFile.mkdirs()
                    apk.text = 'fake apk contents'
                }
            }

            def produceApk = tasks.register('produceApk', ProduceApk) {
                outputDir = layout.buildDirectory.dir('apk')
            }

            def apkFile = produceApk.flatMap { it.outputDir }.map { it.file('app.apk').asFile }

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    apks(IvyPublication) {
                        artifact(apkFile) {
                            classifier = 'debug'
                            extension = 'apk'
                        }
                    }
                }
            }
        """

        when:
        fails 'publishApksPublicationToIvyRepository'

        then:
        configurationCache.assertStateStored()
    }

    def "can publish ivy publication with archive-task-backed artifact when configuration cache is enabled"() {
        // Mirror of the maven archive-task test. Exercises classify()'s hasFixedValue()
        // fast path via the AbstractArchiveTask branch of LazyPublishArtifact.getDelegate().
        // Guards the ivy side of the Shadow plugin fix — no third-party plugin needed.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'ivy-publish'
            apply plugin: 'base'

            group = 'group'
            version = '1.0'

            def customJar = tasks.register('customJar', Jar) {
                archiveClassifier = 'custom'
            }

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    jars(IvyPublication) {
                        artifact(customJar)
                    }
                }
            }
        """

        when:
        succeeds 'publishJarsPublicationToIvyRepository'

        then:
        configurationCache.assertStateStored()
        ivyRepo.module('group', 'root', '1.0').moduleDir.file('root-1.0-custom.jar').exists()
    }

    def "can round-trip configuration cache state for ivy publication with flatMap-provider artifact"() {
        // Store + load: exercises the codec's decode path for a lazy artifact on the
        // ivy side. Uses .flatMap (not .map) to keep the publish task action working
        // on both runs.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'ivy-publish'
            apply plugin: 'base'

            group = 'group'
            version = '1.0'

            def customJar = tasks.register('customJar', Jar) {
                archiveClassifier = 'roundtrip'
            }

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    jars(IvyPublication) {
                        artifact(customJar.flatMap { it.archiveFile })
                    }
                }
            }
        """

        when:
        succeeds 'publishJarsPublicationToIvyRepository'

        then:
        configurationCache.assertStateStored()

        when:
        deleteDirectory(ivyRepo.rootDir)
        succeeds 'publishJarsPublicationToIvyRepository'

        then:
        configurationCache.assertStateLoaded()
        ivyRepo.module('group', 'root', '1.0').moduleDir.file('root-1.0-roundtrip.jar').exists()
    }

    def "can publish ivy publication with no user artifacts when configuration cache is enabled"() {
        // Edge case: publication with zero user-added artifacts. Only auto-generated
        // ivy descriptor + Gradle module metadata are present. Exercises the codec's
        // empty-stream path on classify() and decode of an empty PublicationArtifactSetSpec.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'ivy-publish'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    empty(IvyPublication) {}
                }
            }
        """

        when:
        succeeds 'publishEmptyPublicationToIvyRepository'

        then:
        configurationCache.assertStateStored()

        when:
        deleteDirectory(ivyRepo.rootDir)
        succeeds 'publishEmptyPublicationToIvyRepository'

        then:
        configurationCache.assertStateLoaded()
    }

    def "preserves task dependencies across configuration cache store and load for ivy publication with flatMap-provider artifact"() {
        // Verifies that the codec preserves task dependencies through the store/load
        // round-trip on the ivy side. If the decode path failed to reconstruct the
        // producer-task chain, produceApk would not be scheduled before publish on
        // the CC-load run.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'ivy-publish'

            group = 'group'
            version = '1.0'

            abstract class ProduceApk extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getApk()

                @TaskAction
                void run() {
                    def f = apk.get().asFile
                    f.parentFile.mkdirs()
                    f.text = 'produced by produceApk'
                }
            }

            def produceApk = tasks.register('produceApk', ProduceApk) {
                apk = layout.buildDirectory.file('apk/app.apk')
            }

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    apks(IvyPublication) {
                        artifact(produceApk.flatMap { it.apk }) {
                            classifier = 'debug'
                            extension = 'apk'
                        }
                    }
                }
            }
        """

        when:
        succeeds 'publishApksPublicationToIvyRepository'

        then:
        configurationCache.assertStateStored()
        result.assertTaskExecuted(':produceApk')
        result.assertTaskExecuted(':publishApksPublicationToIvyRepository')

        when:
        deleteDirectory(ivyRepo.rootDir)
        // Force produceApk to re-execute rather than being reported UP-TO-DATE on
        // the CC-load run.
        file('build/apk/app.apk').delete()
        succeeds 'publishApksPublicationToIvyRepository'

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskExecuted(':produceApk')
        result.assertTaskExecuted(':publishApksPublicationToIvyRepository')
        ivyRepo.module('group', 'root', '1.0').moduleDir.file('root-1.0-debug.apk').exists()
    }

    def "can attach a mapped task provider output as an ivy artifact under configuration cache"() {
        // CC-enabled ivy analog of MavenPublishArtifactCustomizationIntegTest.groovy:628.
        // Pins the .flatMap-terminated fast path on the ivy side.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'ivy-publish'
            apply plugin: 'base'

            group = 'group'
            version = '1.0'

            def customJar = tasks.register('customJar', Jar) {
                archiveClassifier = 'classy'
            }

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    ivyCustom(IvyPublication) {
                        artifact(customJar.flatMap { it.archiveFile })
                    }
                }
            }
        """

        when:
        succeeds 'publishIvyCustomPublicationToIvyRepository'

        then:
        configurationCache.assertStateStored()
        ivyRepo.module('group', 'root', '1.0').moduleDir.file('root-1.0-classy.jar').exists()
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

    private void prepareIvyHttpRepository(IvyHttpRepository repository, HttpServer.PasswordCredentials credentials) {
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
