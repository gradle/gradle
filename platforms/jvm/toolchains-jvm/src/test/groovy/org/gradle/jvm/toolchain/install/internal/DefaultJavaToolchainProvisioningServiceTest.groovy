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

package org.gradle.jvm.toolchain.install.internal

import org.gradle.api.GradleException
import org.gradle.authentication.Authentication
import org.gradle.cache.FileLock
import org.gradle.internal.hash.Hashing
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal
import org.gradle.jvm.toolchain.internal.RealizedJavaToolchainRepository
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import org.gradle.jvm.toolchain.internal.install.DefaultJavaToolchainProvisioningService
import org.gradle.jvm.toolchain.internal.install.DefaultJdkCacheDirectory
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainProvisioningException
import org.gradle.platform.Architecture
import org.gradle.platform.OperatingSystem
import org.gradle.platform.internal.CurrentBuildPlatform
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.util.stream.Collectors
import java.util.stream.IntStream

class DefaultJavaToolchainProvisioningServiceTest extends Specification {

    private static final String ARCHIVE_NAME = 'ibm-11-x64-hotspot-linux.zip'
    private static final String UPDATED_ARCHIVE_NAME = 'ibm-11-x64-hotspot-linux-Eclipse-Temurin-11.zip'

    private static final JavaToolchainDownload DOWNLOAD = JavaToolchainDownload.fromUri(URI.create('https://server/whatever'))

    @TempDir
    public File temporaryFolder

    def registry = Mock(JavaToolchainResolverRegistryInternal)
    def downloader = Mock(SecureFileDownloader)
    def cache = Mock(DefaultJdkCacheDirectory)
    def archiveFileLock = Mock(FileLock)
    def buildPlatform = Mock(CurrentBuildPlatform)
    def buildOperationRunner = new TestBuildOperationRunner()
    def spec = Mock(JavaToolchainSpec) {
        getLanguageVersion() >> TestUtil.propertyFactory().property(JavaLanguageVersion).value(JavaLanguageVersion.of(11))
        getVendor() >> TestUtil.propertyFactory().property(JvmVendorSpec).value(JvmVendorSpec.ADOPTIUM)
    }
    def providerFactory = TestUtil.providerFactory()

    def setup() {
        ExternalResourceMetaData downloadResourceMetadata = Mock(ExternalResourceMetaData)
        downloadResourceMetadata.getFilename() >> ARCHIVE_NAME

        ExternalResource downloadResource = Mock(ExternalResource)
        downloadResource.getMetaData() >> downloadResourceMetadata

        downloader.getResourceFor(_ as URI, _ as Collection<Authentication>) >> downloadResource

        cache.acquireWriteLock(_ as File, _ as String) >> archiveFileLock
        cache.getDownloadLocation() >> temporaryFolder
        cache.provisionFromArchive(_ as JavaToolchainSpec, _ as File, _ as URI) >> new File(temporaryFolder, "install_dir")

        buildPlatform.operatingSystem >> OperatingSystem.LINUX
        buildPlatform.architecture >> Architecture.AARCH64
    }

    def "cache is properly locked around provisioning a jdk"() {
        given:
        def toolchainConfiguration = createToolchainConfiguration(true)
        mockRegistry(mockResolver(Optional.of(DOWNLOAD)))

        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, toolchainConfiguration, buildOperationRunner, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * cache.acquireWriteLock(_, _) >> archiveFileLock

        then:
        1 * downloader.download(_, _, _)

        then:
        1 * archiveFileLock.close()


        then:
        List<BuildOperationDescriptor> descriptors = buildOperationRunner.log.getDescriptors()
        descriptors.find { it.name == "Examining toolchain URI " + DOWNLOAD.getUri() }
        descriptors.find { it.name == "Downloading toolchain from URI " + DOWNLOAD.getUri() }
        descriptors.find { it.name == "Unpacking toolchain archive " + UPDATED_ARCHIVE_NAME }
    }

    def "skips downloading if already downloaded"() {
        def toolchainConfiguration = createToolchainConfiguration(true)

        given:
        mockRegistry(mockResolver(Optional.of(DOWNLOAD)))
        new File(temporaryFolder, UPDATED_ARCHIVE_NAME).createNewFile()
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, toolchainConfiguration, buildOperationRunner, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        0 * downloader.download(_, _, _)
    }

    def "skips downloading if cannot satisfy spec"() {
        def toolchainConfiguration = createToolchainConfiguration(true)

        given:
        mockRegistry(mockResolver(Optional.empty()))
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, toolchainConfiguration, buildOperationRunner, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        thrown(ToolchainProvisioningException.class)
        0 * downloader.download(_, _, _)
    }

    def "single resolver fails"() {
        def toolchainConfiguration = createToolchainConfiguration(true)

        given:
        mockRegistry(mockResolver(new GradleException("Something went horribly wrong")))
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, toolchainConfiguration, buildOperationRunner, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        thrown(ToolchainProvisioningException.class)
        0 * downloader.download(_, _, _)
    }

    def "some resolvers fail, but some succeed"() {
        def toolchainConfiguration = createToolchainConfiguration(true)

        given:
        mockRegistry(
            mockResolver(new GradleException("Something went horribly wrong")),
            mockResolver(new GradleException("Something went horribly wrong again")),
            mockResolver(Optional.of(DOWNLOAD)),
            mockResolver(new GradleException("OMG"))
        )
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, toolchainConfiguration, buildOperationRunner, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * downloader.download(DOWNLOAD.getUri(), new File(temporaryFolder, UPDATED_ARCHIVE_NAME), _)
    }

    def "auto download can be disabled"() {
        def toolchainConfiguration = createToolchainConfiguration(false)

        given:
        mockRegistry(mockResolver(Optional.of(DOWNLOAD)))
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, toolchainConfiguration, buildOperationRunner, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        thrown(ToolchainProvisioningException.class)
    }

    def "downloads from url"() {
        def toolchainConfiguration = createToolchainConfiguration(true)

        given:
        mockRegistry(mockResolver(Optional.of(DOWNLOAD)))
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, toolchainConfiguration, buildOperationRunner, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * downloader.download(DOWNLOAD.getUri(), new File(temporaryFolder, UPDATED_ARCHIVE_NAME), _)
    }

    @Issue("https://github.com/gradle/gradle/issues/37104")
    def "archive file name uses provided file name or falls back to URI hash when it is #description"() {
        given:
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, createToolchainConfiguration(true), buildOperationRunner, buildPlatform)
        def metaData = Mock(ExternalResourceMetaData) {
            getFilename() >> filename
        }
        def resource = Mock(ExternalResource) {
            getMetaData() >> metaData
        }

        expect:
        provisioningService.buildFileNameWithDetails(DOWNLOAD.uri, resource, spec) == expected

        where:
        description | filename     | expected
        "provided"  | ARCHIVE_NAME | UPDATED_ARCHIVE_NAME
        "null"      | null         | "jdk-" + Hashing.hashString(DOWNLOAD.uri.toString()).toCompactString() + "-Eclipse-Temurin-11"
        "empty"     | ""           | "jdk-" + Hashing.hashString(DOWNLOAD.uri.toString()).toCompactString() + "-Eclipse-Temurin-11"
    }

    @Issue("https://github.com/gradle/gradle/issues/37104")
    def "archive without a provided file name is downloaded again when the URL token changes"() {
        given:
        def firstUri = URI.create('https://server/jdk?token=first')
        def secondUri = URI.create('https://server/jdk?token=second')
        def firstArchive = new File(temporaryFolder, "jdk-" + Hashing.hashString(firstUri.toString()).toCompactString() + "-Eclipse-Temurin-11")
        def secondArchive = new File(temporaryFolder, "jdk-" + Hashing.hashString(secondUri.toString()).toCompactString() + "-Eclipse-Temurin-11")

        def unnamedResource = Mock(ExternalResource) {
            getMetaData() >> Mock(ExternalResourceMetaData)
        }
        def unnamedDownloader = Mock(SecureFileDownloader) {
            getResourceFor(_ as URI, _ as Collection<Authentication>) >> unnamedResource
        }
        def resolver = Mock(JavaToolchainResolver) {
            resolve(_ as JavaToolchainRequest) >>> [firstUri, firstUri, secondUri].collect { Optional.of(JavaToolchainDownload.fromUri(it)) }
        }
        mockRegistry(resolver)
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, unnamedDownloader, cache, providerFactory, createToolchainConfiguration(true), buildOperationRunner, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * unnamedDownloader.download(firstUri, firstArchive, _) >> { firstArchive.createNewFile() }

        when: "the same URL is provided again"
        provisioningService.tryInstall(spec)

        then: "the downloaded archive is reused"
        0 * unnamedDownloader.download(_, _, _)

        when: "the URL token changes"
        provisioningService.tryInstall(spec)

        then: "the archive gets a different name and is downloaded again"
        1 * unnamedDownloader.download(secondUri, secondArchive, _)
        firstArchive.name != secondArchive.name
    }

    ToolchainConfiguration createToolchainConfiguration(boolean autoDownload) {
        return Mock(ToolchainConfiguration) {
            isDownloadEnabled() >> autoDownload
        }
    }

    private JavaToolchainResolver mockResolver(Optional<JavaToolchainDownload> download) {
        JavaToolchainResolver resolver = Mock(JavaToolchainResolver)
        resolver.resolve(_ as JavaToolchainRequest) >> download
        resolver
    }

    private JavaToolchainResolver mockResolver(Throwable resolveFailure) {
        JavaToolchainResolver resolver = Mock(JavaToolchainResolver)
        resolver.resolve(_ as JavaToolchainRequest) >> { throw resolveFailure }
        resolver
    }

    private void mockRegistry(JavaToolchainResolver... resolvers) {
        def repositories = IntStream.range(0, resolvers.length)
            .mapToObj { i -> mockRepository("resolver" + i, resolvers[i]) }
            .collect(Collectors.toList())

        registry.requestedRepositories() >> repositories
    }

    private RealizedJavaToolchainRepository mockRepository(String repositoryName, JavaToolchainResolver resolver) {
        RealizedJavaToolchainRepository repository = Mock(RealizedJavaToolchainRepository)
        repository.getResolver() >> resolver
        repository.getRepositoryName() >> repositoryName

        repository.getAuthentications(_ as URI) >> Collections.emptyList()
        repository
    }

}
