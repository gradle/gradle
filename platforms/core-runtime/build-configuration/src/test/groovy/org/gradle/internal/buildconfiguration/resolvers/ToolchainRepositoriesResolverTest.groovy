/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildconfiguration.resolvers

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal
import org.gradle.jvm.toolchain.internal.RealizedJavaToolchainRepository
import org.gradle.platform.Architecture
import org.gradle.platform.BuildPlatform
import org.gradle.platform.OperatingSystem
import org.gradle.platform.internal.CustomBuildPlatform
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.stream.Collectors

class ToolchainRepositoriesResolverTest extends Specification {

    private def registry = Mock(JavaToolchainResolverRegistryInternal)

    def "Given no configured repositories When resolve toolchain spec download urls Then expected exception is thrown"() {
        given:
        mockRegistryWithRepositoryResolvedUrl([])
        def resolver = new DefaultToolchainRepositoriesResolver(registry, TestUtil.objectFactory())

        when:
        resolver.resolveToolchainDownloadUrlsByPlatform(JavaVersion.VERSION_11, JvmVendorSpec.AMAZON, JvmImplementation.VENDOR_SPECIFIC)

        then:
        def e = thrown(GradleException)
        e.message == "Toolchain download repositories have not been configured."
        e.cause == null
    }

    def "Given single configured repositories When unable to resolve toolchain spec download urls Then emtpy urls are returned by supported platforms"() {
        given:
        mockRegistryWithRepositoryResolvedUrl([null])
        def resolver = new DefaultToolchainRepositoriesResolver(registry, TestUtil.objectFactory())

        when:
        def result = resolver.resolveToolchainDownloadUrlsByPlatform(JavaVersion.VERSION_11, JvmVendorSpec.AMAZON, JvmImplementation.VENDOR_SPECIFIC)

        then:
        expectedToolchainSupportedPlatforms().containsAll(result.keySet())
        result.values().toSet().toList() == [Optional.empty()]
    }

    def "Given single configured repositories When resolve toolchain spec download urls Then expected urls are returned by supported platforms"() {
        given:
        mockRegistryWithRepositoryResolvedUrl(['https://server/whatever1'])
        def resolver = new DefaultToolchainRepositoriesResolver(registry, TestUtil.objectFactory())

        when:
        def result = resolver.resolveToolchainDownloadUrlsByPlatform(JavaVersion.VERSION_17, JvmVendorSpec.AZUL, JvmImplementation.VENDOR_SPECIFIC)

        then:
        expectedToolchainSupportedPlatforms().containsAll(result.keySet())
        result.values().toSet().toList() == [Optional.of(URI.create('https://server/whatever1'))]
    }

    def "Given multiple configured repositories When resolve toolchain spec download urls Then first resolved valid urls are returned by supported platforms"() {
        given:
        mockRegistryWithRepositoryResolvedUrl([null, 'https://server/whatever1', 'https://server/whatever2'])
        def resolver = new DefaultToolchainRepositoriesResolver(registry, TestUtil.objectFactory())

        when:
        def result = resolver.resolveToolchainDownloadUrlsByPlatform(JavaVersion.VERSION_1_8, JvmVendorSpec.IBM, JvmImplementation.J9)

        then:
        expectedToolchainSupportedPlatforms().containsAll(result.keySet())
        result.values().toSet().toList() == [Optional.of(URI.create('https://server/whatever1'))]
    }

    private List<BuildPlatform> expectedToolchainSupportedPlatforms() {
        return [Architecture.AARCH64, Architecture.X86_64].stream()
            .flatMap(architecture ->
                OperatingSystem.values().toList().stream().map(operatingSystem ->
                    TestUtil.objectFactory().newInstance(CustomBuildPlatform.class, architecture, operatingSystem)
                )
            )
            .collect(Collectors.toList())
    }

    private void mockRegistryWithRepositoryResolvedUrl(List<String> repositoriesResolvedUrls) {
        def mockRepositories = []
        repositoriesResolvedUrls.forEach { url ->
            def javaToolchainDownload = url != null ? Optional.of(JavaToolchainDownload.fromUri(URI.create(url))) : Optional.empty()

            JavaToolchainResolver resolver = Mock(JavaToolchainResolver)
            resolver.resolve(_ as JavaToolchainRequest) >> javaToolchainDownload
            RealizedJavaToolchainRepository repository = Mock(RealizedJavaToolchainRepository) {
                getResolver() >> resolver
                getAuthentications(_ as URI) >> Collections.emptyList()
            }
            mockRepositories.add(repository)
        }

        registry.requestedRepositories() >> mockRepositories
    }
}
