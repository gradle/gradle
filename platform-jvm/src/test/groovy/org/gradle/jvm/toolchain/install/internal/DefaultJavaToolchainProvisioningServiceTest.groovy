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
        def lock = Mock(FileLock)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def spec = Mock(JavaToolchainSpec)
        def operationExecutor = new TestBuildOperationExecutor()
        def providerFactory = createProviderFactory("true")

        given:
        binary.canProvideMatchingJdk(spec) >> true
        binary.toFilename(spec) >> 'jdk-123.zip'
        def downloadLocation = Mock(File)
        downloadLocation.name >> "filename.zip"
        cache.getDownloadLocation(_ as String) >> downloadLocation

        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, cache, providerFactory, operationExecutor)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * cache.acquireWriteLock(downloadLocation, _) >> lock

        then:
        1 * binary.download(_, _) >> Optional.empty()

        then:
        1 * lock.close()

        then:
        operationExecutor.log.getDescriptors().find {it.displayName == "Provisioning toolchain filename.zip"}
        operationExecutor.log.getDescriptors().find {it.displayName == "Unpacking toolchain archive"}
    }

    def "skips downloading if already downloaded"() {
        def cache = Mock(JdkCacheDirectory)
        def lock = Mock(FileLock)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("true")

        given:
        binary.canProvideMatchingJdk(spec) >> true
        cache.acquireWriteLock(_, _) >> lock
        binary.toFilename(spec) >> 'jdk-123.zip'
        def downloadLocation = new File(temporaryFolder, "jdk.zip")
        downloadLocation.createNewFile()
        cache.getDownloadLocation(_ as String) >> downloadLocation
        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        provisioningService.tryInstall(spec)

        then:
        0 * binary.download(_, _)
    }

    def "skips downloading if cannot satisfy spec"() {
        def cache = Mock(JdkCacheDirectory)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("true")

        given:
        binary.canProvideMatchingJdk(spec) >> false
        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        def result = provisioningService.tryInstall(spec)

        then:
        !result.isPresent()
        0 * binary.download(_, _)
    }

    def "auto download can be disabled"() {
        def cache = Mock(JdkCacheDirectory)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def spec = Mock(JavaToolchainSpec)
        def providerFactory = createProviderFactory("false")

        given:
        binary.canProvideMatchingJdk(spec) >> true
        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, cache, providerFactory, new TestBuildOperationExecutor())

        when:
        def result = provisioningService.tryInstall(spec)

        then:
        !result.isPresent()
    }

    ProviderFactory createProviderFactory(String propertyValue) {
        return Mock(ProviderFactory) {
            gradleProperty("org.gradle.java.installations.auto-download") >> Providers.ofNullable(propertyValue)
        }
    }

}
