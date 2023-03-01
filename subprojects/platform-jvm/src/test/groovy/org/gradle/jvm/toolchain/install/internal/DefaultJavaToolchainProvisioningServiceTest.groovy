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

import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.Authentication
import org.gradle.cache.FileLock
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal
import org.gradle.jvm.toolchain.internal.RealizedJavaToolchainRepository
import org.gradle.jvm.toolchain.internal.ToolchainDownloadFailedException
import org.gradle.jvm.toolchain.internal.install.DefaultJavaToolchainProvisioningService
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader
import org.gradle.platform.BuildPlatform
import spock.lang.Specification
import spock.lang.TempDir

class DefaultJavaToolchainProvisioningServiceTest extends Specification {

    private static final String ARCHIVE_NAME = 'ibm-11-x64-hotspot-linux.zip'

    private static final JavaToolchainDownload DOWNLOAD = JavaToolchainDownload.fromUri(URI.create('https://server/whatever'))

    @TempDir
    public File temporaryFolder

    def registry = Mock(JavaToolchainResolverRegistryInternal)
    def downloader = Mock(SecureFileDownloader)
    def cache = Mock(JdkCacheDirectory)
    def archiveFileLock = Mock(FileLock)
    def buildPlatform = Mock(BuildPlatform)

    def setup() {
        ExternalResourceMetaData downloadResourceMetadata = Mock(ExternalResourceMetaData)
        downloadResourceMetadata.getFilename() >> ARCHIVE_NAME

        ExternalResource downloadResource = Mock(ExternalResource)
        downloadResource.getMetaData() >> downloadResourceMetadata

        downloader.getResourceFor(_ as URI, _ as Collection<Authentication>) >> downloadResource

        cache.acquireWriteLock(_ as File, _ as String) >> archiveFileLock
        cache.getDownloadLocation() >> temporaryFolder
        cache.provisionFromArchive(_ as JavaToolchainSpec, _ as File, _ as URI) >> new File(temporaryFolder, "install_dir")
    }

    def "cache is properly locked around provisioning a jdk"() {
        def spec = Mock(JavaToolchainSpec)

        def operationExecutor = new TestBuildOperationExecutor()
        def providerFactory = createProviderFactory("true")

        given:
        mockRegistry(Optional.of(DOWNLOAD))

        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, operationExecutor, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * cache.acquireWriteLock(_, _) >> archiveFileLock

        then:
        1 * downloader.download(_, _, _)

        then:
        1 * archiveFileLock.close()


        then:
        List<BuildOperationDescriptor> descriptors = operationExecutor.log.getDescriptors()
        descriptors.find {it.name == "Examining toolchain URI " + DOWNLOAD.getUri() }
        descriptors.find {it.name == "Downloading toolchain from URI " + DOWNLOAD.getUri() }
        descriptors.find {it.name == "Unpacking toolchain archive " + ARCHIVE_NAME }
    }

    def "skips downloading if already downloaded"() {
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("true")

        given:
        mockRegistry(Optional.of(DOWNLOAD))
        new File(temporaryFolder, ARCHIVE_NAME).createNewFile()
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, new TestBuildOperationExecutor(), buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        0 * downloader.download(_, _, _)
    }

    def "skips downloading if cannot satisfy spec"() {
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("true")

        given:
        mockRegistry(Optional.empty())
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, new TestBuildOperationExecutor(), buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        thrown(ToolchainDownloadFailedException.class)
        0 * downloader.download(_, _, _)
    }

    def "auto download can be disabled"() {
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("false")

        given:
        mockRegistry(Optional.of(DOWNLOAD))
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, new TestBuildOperationExecutor(), buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        thrown(ToolchainDownloadFailedException.class)
    }

    def "downloads from url"() {
        def spec = Mock(JavaToolchainSpec)
        def operationExecutor = new TestBuildOperationExecutor()
        def providerFactory = createProviderFactory("true")

        given:
        mockRegistry(Optional.of(DOWNLOAD))
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, operationExecutor, buildPlatform)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * downloader.download(DOWNLOAD.getUri(), new File(temporaryFolder, ARCHIVE_NAME), _)
    }

    ProviderFactory createProviderFactory(String propertyValue) {
        return Mock(ProviderFactory) {
            gradleProperty("org.gradle.java.installations.auto-download") >> Providers.ofNullable(propertyValue)
        }
    }

    private void mockRegistry(Optional<URI> uri) {
        JavaToolchainResolver resolver = Mock(JavaToolchainResolver)
        resolver.resolve(_ as JavaToolchainRequest) >> uri
        RealizedJavaToolchainRepository repository = Mock(RealizedJavaToolchainRepository)
        repository.getResolver() >> resolver

        repository.getAuthentications(_ as URI) >> Collections.emptyList()

        registry.requestedRepositories() >> Collections.singletonList(repository)
    }

}
