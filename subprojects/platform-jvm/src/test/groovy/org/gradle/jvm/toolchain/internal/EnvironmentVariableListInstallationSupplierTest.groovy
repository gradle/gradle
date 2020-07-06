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

package org.gradle.jvm.toolchain.internal


import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class EnvironmentVariableListInstallationSupplierTest extends Specification {

    def "supplies no installations for absent property"() {
        given:
        def supplier = createSupplier(null)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for empty property"() {
        given:
        def supplier = createSupplier("")

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single path"() {
        given:
        def supplier = createSupplier("JDK8")

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths(["/path/jdk8"])
        directories*.source == ["environment variable 'JDK8'"]
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        def supplier = createSupplier("JDK8,JDK9")

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths(["/path/jdk8", "/path/jdk9"])
    }

    def "supplies installations with malformed variables"() {
        given:
        def supplier = createSupplier(",JDK9 ")

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths(["/path/jdk9"])
    }

    def directoriesAsStablePaths(Set<InstallationLocation> actualDirectories) {
        actualDirectories*.location.absolutePath.sort()
    }

    def stablePaths(List<String> expectedPaths) {
        expectedPaths.replaceAll({ String s -> systemSpecificAbsolutePath(s) })
        expectedPaths
    }

    private EnvironmentVariableListInstallationSupplier createSupplier(String propertyValue) {
        new EnvironmentVariableListInstallationSupplier(createProviderFactory(propertyValue))
    }

    private ProviderFactory createProviderFactory(String propertyValue) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.fromEnv") >> mockProvider(propertyValue)
        providerFactory.environmentVariable("JDK8") >> mockProvider("/path/jdk8")
        providerFactory.environmentVariable("JDK9") >> mockProvider("/path/jdk9")
        providerFactory.environmentVariable("") >> mockProvider(null)
        providerFactory
    }

    Provider<String> mockProvider(String value) {
        def provider = new DefaultProperty(PropertyHost.NO_OP, String)
        provider.set(value)
        provider
    }

}
