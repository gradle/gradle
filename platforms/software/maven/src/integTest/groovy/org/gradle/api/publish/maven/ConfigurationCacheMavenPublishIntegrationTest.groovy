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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.internal.GUtil
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.util.internal.GFileUtils.deleteDirectory
import static org.gradle.util.internal.GFileUtils.listFiles

@Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly")
class ConfigurationCacheMavenPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()

    def configurationCache = newConfigurationCacheFixture()

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

    def "can publish maven publication metadata to remote repository"() {
        def username = "someuser"
        def password = "somepassword"
        def projectConfig = configureProject(username, password, "mavenRepo", false)
        def metadataFile = file('build/publications/maven/module.json')

        expect:
        !GUtil.isSecureUrl(server.uri)

        when:
        prepareMavenHttpRepository(projectConfig.remoteRepo, new HttpServer.PasswordCredentials(username, password))
        run(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertStateStored()
        metadataFile.exists()

        when:
        def storeTimeRepo = mavenRepoFiles()
        def storeTimeMetadata = metadataFile.text
        metadataFile.delete()
        deleteDirectory(mavenRepo.rootDir)

        prepareMavenHttpRepository(projectConfig.remoteRepo, new HttpServer.PasswordCredentials(username, password))
        run(*(projectConfig.tasks))
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
                    name = "${repositoryName}"
                    url = "${remoteRepo.uri}"
                    allowInsecureProtocol = true
                    // no credentials
                }
            }
        """)
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
        run(*tasks)
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
        run(*tasks)
        server.resetExpectations()

        then:
        configurationCache.assertStateLoaded()
        def loadTimeRepo = mavenRepoFiles()
        storeTimeRepo == loadTimeRepo
        def loadTimeMetadata = metadataFile.text
        storeTimeMetadata == loadTimeMetadata
    }

    @Issue("https://github.com/gradle/gradle/issues/22618")
    def "using unsafe credentials provider with configuration cache falls back into vintage"() {
        def username = "someuser"
        def password = "somepassword"
        def repositoryName = "testMavenRepo"
        def projectConfig = configureProject(username, password, repositoryName, true)

        when:
        prepareMavenHttpRepository(projectConfig.remoteRepo, new HttpServer.PasswordCredentials(username, password))
        run(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertNoConfigurationCache()
        output.contains("Configuration cache disabled because incompatible task was found.")
    }

    /*
     For credentials provided using credential providers, the repository name is used as the identity
     of the provider. Credential provider identities must be made exclusively of letters and digits.
     So, when using credential providers with repositories, the repository name must also be a valid
     provider identity.

     However, for inlined/unsafe credentials, since providers are not used, we should not impose such limitations.
     */
    @Issue("https://github.com/gradle/gradle/issues/22618")
    def "can use identity-incompatible repository name credentials provider as that falls back to vintage"() {
        def username = "someuser"
        def password = "somepassword"
        def repositoryName = "repo-with-invalid-identity-name"
        def projectConfig = configureProject(username, password, repositoryName, true)

        when:
        prepareMavenHttpRepository(projectConfig.remoteRepo, new HttpServer.PasswordCredentials(username, password))
        run(*(projectConfig.tasks))
        server.resetExpectations()

        then:
        configurationCache.assertNoConfigurationCache()
        output.contains("Configuration cache disabled because incompatible task was found.")
    }

    def "can publish maven publication metadata to local repository"() {
        settingsFile "rootProject.name = 'root'"
        buildFile buildFileConfiguration("""
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
        """)
        def metadataFile = file('build/publications/maven/module.json')
        def tasks = [
            'generateMetadataFileForMavenPublication',
            'generatePomFileForMavenPublication',
            'publishMavenPublicationToMavenRepository',
            'publishAllPublicationsToMavenRepository'
        ]

        when:
        run(*tasks)

        then:
        configurationCache.assertStateStored()
        metadataFile.exists()

        when:
        def storeTimeRepo = mavenRepoFiles()
        def storeTimeMetadata = metadataFile.text
        metadataFile.delete()
        deleteDirectory(mavenRepo.rootDir)
        run(*tasks)

        then:
        configurationCache.assertStateLoaded()
        def loadTimeRepo = mavenRepoFiles()
        storeTimeRepo == loadTimeRepo
        def loadTimeMetadata = metadataFile.text
        storeTimeMetadata == loadTimeMetadata
    }

    @Issue("https://github.com/gradle/gradle/issues/29253")
    def "configuration cache state can be stored for maven publication with artifact from mapped task output"() {
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            abstract class ProduceApk extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void run() {
                    def apk = outputDir.file("app.apk").get().asFile
                    apk.parentFile.mkdirs()
                    apk.text = "fake apk contents"
                }
            }

            def produceApk = tasks.register("produceApk", ProduceApk) {
                outputDir = layout.buildDirectory.dir("apk")
            }

            // Mirrors the AGP-style pattern from the issue:
            //   variant.artifacts.get(SingleArtifact.APK).map { it.singleApk() }
            // The outer .map yields a TransformBackedProvider whose beforeRead
            // guard rejects reads before the producing task has run.
            def apkFile = produceApk.flatMap { it.outputDir }.map { it.file("app.apk").asFile }

            publishing {
                publications {
                    apks(MavenPublication) {
                        artifact(apkFile) {
                            classifier = "debug"
                            extension = "apk"
                        }
                    }
                }
            }
        """

        // The publish task's @TaskAction still fails downstream: SerializableMavenArtifact's
        // constructor (invoked from DefaultMavenPublication.asNormalisedPublication via
        // normalizedArtifactFor) eagerly calls artifact.getFile() on the LazyPublishArtifact,
        // which invokes TransformBackedProvider.beforeRead. That guard trips for `.map { ... }`
        // chains whose terminus is a raw java.io.File (rather than a FileSystemLocation).
        // Issue #29253 reports only the CC-store failure, which IS fixed here — the downstream
        // execution failure is a separate defect tracked by the umbrella issue #24329.
        //
        // `configurationCache.assertStateStored()` is the guard for #29253: it fails if the
        // CC entry was not stored (which is exactly what the codec bug prevented).
        when:
        fails "publishApksPublicationToMavenLocal"

        then:
        configurationCache.assertStateStored()
        !errorOutput.contains("Configuration cache state could not be cached")
        !errorOutput.contains("error writing value of type 'org.gradle.api.internal.file.UnionFileCollection'")
    }

    def "can publish maven publication with archive-task-backed artifact when configuration cache is enabled"() {
        // Exercises classify()'s hasFixedValue() fast path via the AbstractArchiveTask
        // branch of LazyPublishArtifact.getDelegate(). The Shadow plugin's shape at
        // configCacheSmokeTest depends on this working; this in-repo test is the local
        // regression guard so we do not have to rely on the smoke-test CI variant to
        // catch a change to classify()'s dispatch.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'maven-publish'
            apply plugin: 'base'

            group = 'group'
            version = '1.0'

            def customJar = tasks.register('customJar', Jar) {
                archiveClassifier = 'custom'
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    jars(MavenPublication) {
                        artifact(customJar)
                    }
                }
            }
        """

        when:
        succeeds 'publishJarsPublicationToMavenRepository'

        then:
        configurationCache.assertStateStored()
        mavenRepo.module('group', 'root', '1.0').moduleDir.file('root-1.0-custom.jar').exists()
    }

    def "can round-trip configuration cache state for maven publication with flatMap-provider artifact"() {
        // Store + load: exercises the codec's decode path for a lazy artifact. Uses .flatMap
        // (not .map) because FlatMapProvider has no beforeRead guard, so the publish task
        // action can actually complete on both runs. The .map-based reproducer above cannot
        // round-trip today because of the separate downstream SerializableMavenArtifact
        // failure at task execution.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'maven-publish'
            apply plugin: 'base'

            group = 'group'
            version = '1.0'

            def customJar = tasks.register('customJar', Jar) {
                archiveClassifier = 'roundtrip'
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    jars(MavenPublication) {
                        artifact(customJar.flatMap { it.archiveFile })
                    }
                }
            }
        """

        when:
        succeeds 'publishJarsPublicationToMavenRepository'

        then:
        configurationCache.assertStateStored()

        when:
        deleteDirectory(mavenRepo.rootDir)
        succeeds 'publishJarsPublicationToMavenRepository'

        then:
        configurationCache.assertStateLoaded()
        mavenRepo.module('group', 'root', '1.0').moduleDir.file('root-1.0-roundtrip.jar').exists()
    }

    def "can publish maven publication with mixed artifact shapes when configuration cache is enabled"() {
        // Exercises every classify() branch in a single publication: an archive-task-backed
        // artifact (Shadow shape → hasFixedValue on LazyPublishArtifact whose value is a
        // Task), a plain File-backed artifact (falls through to the outer else — non-
        // LazyPublishArtifact — branch), and a flatMap-provider artifact (hasFixedValue on
        // LazyPublishArtifact whose value is a RegularFile). Verifies the codec's list-
        // preservation semantics and the decode path handle a heterogeneous entry list.
        settingsFile "rootProject.name = 'root'"
        file('static-artifact.txt').text = 'static content'

        buildFile """
            apply plugin: 'maven-publish'
            apply plugin: 'base'

            group = 'group'
            version = '1.0'

            def customJar = tasks.register('customJar', Jar) {
                archiveClassifier = 'jar-artifact'
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    mixed(MavenPublication) {
                        artifact(customJar)                            // archive-task branch
                        artifact(file('static-artifact.txt')) {        // non-LazyPublishArtifact branch
                            classifier = 'static'
                            extension = 'txt'
                        }
                        artifact(customJar.flatMap { it.archiveFile }) {  // hasFixedValue on lazy branch
                            classifier = 'flatmap'
                        }
                    }
                }
            }
        """

        when:
        succeeds 'publishMixedPublicationToMavenRepository'

        then:
        configurationCache.assertStateStored()

        when:
        deleteDirectory(mavenRepo.rootDir)
        succeeds 'publishMixedPublicationToMavenRepository'

        then:
        configurationCache.assertStateLoaded()
        def moduleDir = mavenRepo.module('group', 'root', '1.0').moduleDir
        moduleDir.file('root-1.0-jar-artifact.jar').exists()
        moduleDir.file('root-1.0-static.txt').exists()
        moduleDir.file('root-1.0-flatmap.jar').exists()
    }

    def "configuration cache state can be stored for maven publication with map-provider artifact returning RegularFile"() {
        // Same shape as issue #29253 but the .map transformer returns a RegularFile
        // (a FileSystemLocation), not a raw java.io.File. Isolates whether the
        // downstream SerializableMavenArtifact failure is .map-specific (yes) or
        // terminal-type-specific: even a FileSystemLocation-returning .map still
        // trips beforeRead on TransformBackedProvider at the publish task action.
        // CC store succeeds either way — that is what our fix delivers.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'maven-publish'

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

            def apkFile = produceApk.flatMap { it.outputDir }.map { it.file('app.apk') }

            publishing {
                publications {
                    apks(MavenPublication) {
                        artifact(apkFile) {
                            classifier = 'debug'
                            extension = 'apk'
                        }
                    }
                }
            }
        """

        when:
        fails 'publishApksPublicationToMavenLocal'

        then:
        configurationCache.assertStateStored()
    }

    def "can publish maven publication with no user artifacts when configuration cache is enabled"() {
        // Edge case: publication with zero user-added artifacts. Only auto-generated
        // metadata artifacts (pom, module descriptor) are present. Exercises the
        // codec's empty-stream path on classify() and the decode-side reconstruction
        // of an empty PublicationArtifactSetSpec.entries list.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    empty(MavenPublication) {}
                }
            }
        """

        when:
        succeeds 'publishEmptyPublicationToMavenRepository'

        then:
        configurationCache.assertStateStored()

        when:
        deleteDirectory(mavenRepo.rootDir)
        succeeds 'publishEmptyPublicationToMavenRepository'

        then:
        configurationCache.assertStateLoaded()
    }

    def "preserves task dependencies across configuration cache store and load for maven publication with flatMap-provider artifact"() {
        // Direct verification that the codec preserves task dependencies through the
        // store/load round-trip. If our decode path (fileCollectionFactory.resolving(entries))
        // failed to reconstruct the producer-task chain, the produceApk task would not be
        // scheduled before publish on the CC-load run, and the artifact file would be
        // missing (or the build would fail).
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'maven-publish'

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
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    apks(MavenPublication) {
                        artifact(produceApk.flatMap { it.apk }) {
                            classifier = 'debug'
                            extension = 'apk'
                        }
                    }
                }
            }
        """

        when:
        succeeds 'publishApksPublicationToMavenRepository'

        then:
        configurationCache.assertStateStored()
        result.assertTaskExecuted(':produceApk')
        result.assertTaskExecuted(':publishApksPublicationToMavenRepository')

        when:
        deleteDirectory(mavenRepo.rootDir)
        // Force produceApk to actually re-execute on the CC-load run rather than being
        // reported UP-TO-DATE — the assertion below wants proof it was scheduled AND ran,
        // which is only true if its output has been invalidated.
        file('build/apk/app.apk').delete()
        succeeds 'publishApksPublicationToMavenRepository'

        then:
        configurationCache.assertStateLoaded()
        // The load-time task graph must still include produceApk before publish.
        result.assertTaskExecuted(':produceApk')
        result.assertTaskExecuted(':publishApksPublicationToMavenRepository')
        mavenRepo.module('group', 'root', '1.0').moduleDir.file('root-1.0-debug.apk').exists()
    }

    def "can attach a mapped task provider output as an artifact under configuration cache"() {
        // CC-enabled mirror of MavenPublishArtifactCustomizationIntegTest.groovy:628.
        // Pins the .flatMap-terminated fast path: FlatMapProvider has no beforeRead
        // guard, so classify() sees hasFixedValue() and the codec captures it as a
        // fixed-value provider. If a future refactor tightens hasFixedValue() semantics
        // and this path regresses, this test fails immediately.
        settingsFile "rootProject.name = 'root'"
        buildFile """
            apply plugin: 'maven-publish'
            apply plugin: 'base'

            group = 'group'
            version = '1.0'

            def customJar = tasks.register('customJar', Jar) {
                archiveClassifier = 'classy'
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    mavenCustom(MavenPublication) {
                        artifact(customJar.flatMap { it.archiveFile })
                    }
                }
            }
        """

        when:
        succeeds 'publishMavenCustomPublicationToMavenRepository'

        then:
        configurationCache.assertStateStored()
        mavenRepo.module('group', 'root', '1.0').moduleDir.file('root-1.0-classy.jar').exists()
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
                    username = '${username}'
                    password = '${password}'
                }
            """
        } else {
            credentialsBlock = "credentials(PasswordCredentials)"
            configureRepositoryCredentials(username, password, repositoryName)
        }
        buildFile buildFileConfiguration("""
            repositories {
                maven {
                    name = "${repositoryName}"
                    url = "${remoteRepo.uri}"
                    allowInsecureProtocol = true
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

    private void prepareMavenHttpRepository(MavenHttpRepository repository, HttpServer.PasswordCredentials credentials) {
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
