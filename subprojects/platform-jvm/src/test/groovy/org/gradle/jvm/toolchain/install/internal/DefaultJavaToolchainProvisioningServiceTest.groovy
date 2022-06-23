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
import org.gradle.jvm.toolchain.JavaToolchainSpec
import spock.lang.Specification
import spock.lang.TempDir

class DefaultJavaToolchainProvisioningServiceTest extends Specification {

    @TempDir
    public File temporaryFolder

    def "cache is properly locked around provisioning a jdk"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def lock = Mock(FileLock)
        def spec = Mock(JavaToolchainSpec)
        def operationExecutor = new TestBuildOperationExecutor()
        def providerFactory = createProviderFactory("true")
        def archiveName = "jdk-123.zip"

        given:
        binary.canProvide(spec) >> true
        binary.toUri(spec) >> URI.create('http://server/' + archiveName)
        binary.toArchiveFileName(spec) >> archiveName

        def downloadLocation = Mock(File)
        downloadLocation.name >> "dir/" + archiveName
        cache.getDownloadLocation(_ as String) >> downloadLocation

        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, downloader, cache, providerFactory, operationExecutor)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * cache.acquireWriteLock(downloadLocation, _) >> lock

        then:
        1 * downloader.download(_, _)

        then:
        1 * lock.close()

        then:
        operationExecutor.log.getDescriptors().find {it.displayName == "Provisioning toolchain " + downloadLocation.name}
        operationExecutor.log.getDescriptors().find {it.displayName == "Unpacking toolchain archive"}
    }

    def "skips downloading if already downloaded"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def lock = Mock(FileLock)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("true")

        given:
        binary.canProvide(spec) >> true
        cache.acquireWriteLock(_, _) >> lock
        binary.toArchiveFileName(spec) >> 'jdk-123.zip'
        def downloadLocation = new File(temporaryFolder, "jdk.zip")
        downloadLocation.createNewFile()
        cache.getDownloadLocation(_ as String) >> downloadLocation
        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, downloader, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        provisioningService.tryInstall(spec)

        then:
        0 * downloader.download(_, _)
    }

    def "skips downloading if cannot satisfy spec"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("true")

        given:
        binary.canProvide(spec) >> false
        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, downloader, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        def result = provisioningService.tryInstall(spec)

        then:
        !result.isPresent()
        0 * downloader.download(_, _)
    }

    def "auto download can be disabled"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("false")

        given:
        binary.canProvide(spec) >> true
        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, downloader, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        def result = provisioningService.tryInstall(spec)

        then:
        !result.isPresent()
    }

    def "downloads from url"() {
        def cache = Mock(JdkCacheDirectory)
        def downloader = Mock(FileDownloader)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def lock = Mock(FileLock)
        def spec = Mock(JavaToolchainSpec)
        def operationExecutor = new TestBuildOperationExecutor()
        def providerFactory = createProviderFactory("true")
        def archiveName = "file.tgz"

        given:
        binary.canProvide(spec) >> true
        binary.toUri(spec) >> URI.create("uri")
        binary.toArchiveFileName(spec) >> archiveName

        def downloadLocation = Mock(File)
        downloadLocation.name >> "dir/" + archiveName
        cache.getDownloadLocation(_ as String) >> downloadLocation
        cache.acquireWriteLock(_ as File, _ as String) >> lock

        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, downloader, cache, providerFactory, operationExecutor)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * downloader.download(URI.create("uri"), downloadLocation)
    }

    ProviderFactory createProviderFactory(String propertyValue) {
        return Mock(ProviderFactory) {
            gradleProperty("org.gradle.java.installations.auto-download") >> Providers.ofNullable(propertyValue)
        }
    }

}
