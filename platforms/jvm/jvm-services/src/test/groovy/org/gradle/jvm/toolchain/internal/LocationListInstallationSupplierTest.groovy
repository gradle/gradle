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

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import spock.lang.Specification

class LocationListInstallationSupplierTest extends Specification {

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
        def supplier = createSupplier("/foo/bar")

        when:
        def directories = supplier.get()

        then:
        directories*.location == [new File("/foo/bar")]
        directories*.source == ["Gradle property 'org.gradle.java.installations.paths'"]
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        def supplier = createSupplier("/foo/bar,/foo/123")

        when:
        def directories = supplier.get()

        then:
        directories*.location.sort() == [new File("/foo/123"), new File("/foo/bar")]
    }

    private createSupplier(String propertyValue) {
        new LocationListInstallationSupplier(createProviderFactory(propertyValue), createFileResolver())
    }

    private ProviderFactory createProviderFactory(String propertyValue) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.paths") >> Providers.ofNullable(propertyValue)
        providerFactory
    }

    private FileResolver createFileResolver() {
        def fileResolver = Mock(FileResolver)
        fileResolver.resolve(_) >> {String path -> new File(path)}
        fileResolver
    }

}
