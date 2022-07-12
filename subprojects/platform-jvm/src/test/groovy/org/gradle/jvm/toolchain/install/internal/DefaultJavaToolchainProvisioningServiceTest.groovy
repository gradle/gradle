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
import org.gradle.cache.FileLock
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.jvm.toolchain.JavaToolchainRepository
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.JavaToolchainRepositoryRegistryInternal
import org.gradle.jvm.toolchain.internal.install.AdoptOpenJdkRemoteBinary
import org.gradle.jvm.toolchain.internal.install.DefaultJavaToolchainProvisioningService
import org.gradle.jvm.toolchain.internal.install.FileDownloader
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory
import spock.lang.Specification
import spock.lang.TempDir

class DefaultJavaToolchainProvisioningServiceTest extends Specification {

    @TempDir
    public File temporaryFolder

    def binary = Mock(AdoptOpenJdkRemoteBinary)
    def registry = Mock(JavaToolchainRepositoryRegistryInternal)

    def setup() {
        registry.requestedRepositories() >> Collections.singletonList(binary)
    }

    def "cache is properly locked around provisioning a jdk"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)

        def lock = Mock(FileLock)
        def spec = Mock(JavaToolchainSpec)

        def operationExecutor = new TestBuildOperationExecutor()
        def providerFactory = createProviderFactory("true")
        def archiveName = "jdk-123.zip"

        given:
        binary.toUri(spec) >> Optional.of(URI.create('http://server/' + archiveName))
        binary.toMetadata(spec) >> Optional.of(new MockMetadata())

        cache.getDownloadLocation(_ as String) >> { String filename -> new File("dir/" + filename) }

        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, operationExecutor)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * cache.acquireWriteLock(_, _) >> lock

        then:
        1 * downloader.download(_, _)

        then:
        1 * lock.close()

        then:
        operationExecutor.log.getDescriptors().find {it.displayName == "Provisioning toolchain " + MockMetadata.archiveFileName}
        operationExecutor.log.getDescriptors().find {it.displayName == "Unpacking toolchain archive"}
    }

    def "skips downloading if already downloaded"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def lock = Mock(FileLock)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("true")

        given:
        binary.toUri(spec) >> Optional.of(URI.create("uri"))
        cache.acquireWriteLock(_, _) >> lock
        binary.toMetadata(spec) >> Optional.of(new MockMetadata())
        new File(temporaryFolder, MockMetadata.archiveFileName).createNewFile()
        cache.getDownloadLocation(_ as String) >> {String fileName -> new File(temporaryFolder, fileName)}
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        provisioningService.tryInstall(spec)

        then:
        0 * downloader.download(_, _)
    }

    def "skips downloading if cannot satisfy spec"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("true")

        given:
        binary.toUri(spec) >> Optional.empty()
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        def result = provisioningService.tryInstall(spec)

        then:
        !result.isPresent()
        0 * downloader.download(_, _)
    }

    def "auto download can be disabled"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("false")

        given:
        binary.toUri(spec) >> Optional.of(URI.create("uri"))
        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        def result = provisioningService.tryInstall(spec)

        then:
        !result.isPresent()
    }

    def "downloads from url"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def lock = Mock(FileLock)
        def spec = Mock(JavaToolchainSpec)
        def operationExecutor = new TestBuildOperationExecutor()
        def providerFactory = createProviderFactory("true")
        def archiveName = "file.tgz"

        given:
        binary.toUri(spec) >> Optional.of(URI.create("uri"))
        binary.toMetadata(spec) >> Optional.of(new MockMetadata())

        cache.getDownloadLocation(_ as String) >> {String fileName -> new File(fileName)}
        cache.acquireWriteLock(_ as File, _ as String) >> lock

        def provisioningService = new DefaultJavaToolchainProvisioningService(registry, downloader, cache, providerFactory, operationExecutor)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * downloader.download(URI.create("uri"), new File(MockMetadata.archiveFileName))
    }

    ProviderFactory createProviderFactory(String propertyValue) {
        return Mock(ProviderFactory) {
            gradleProperty("org.gradle.java.installations.auto-download") >> Providers.ofNullable(propertyValue)
        }
    }

    private class MockMetadata implements JavaToolchainRepository.Metadata {

        static String archiveFileName = "ibm-11-x64-hotspot-linux.zip"

        @Override
        String fileExtension() {
            return "zip"
        }

        @Override
        String vendor() {
            return "ibm"
        }

        @Override
        String languageLevel() {
            return "11"
        }

        @Override
        String operatingSystem() {
            return "linux"
        }

        @Override
        String implementation() {
            return "hotspot"
        }

        @Override
        String architecture() {
            return "x64"
        }
    }

}
