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

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.logging.ToStringLogger
import spock.lang.Specification
import spock.lang.Unroll

class LocationListInstallationSupplierTest extends Specification {

    def supplier
    def logger = new ToStringLogger()
    ProviderFactory providerFactory
    def currentGradlePropertyValue

    void setup() {
        providerFactory = Mock(ProviderFactory)
        def provider = Mock(Provider)
        provider.get() >> { currentGradlePropertyValue }
        provider.isPresent() >> { currentGradlePropertyValue != null }
        provider.forUseAtConfigurationTime() >> provider
        providerFactory.gradleProperty("org.gradle.java.installations.paths") >> provider
        supplier = new LocationListInstallationSupplier(providerFactory, logger) {
            @Override
            boolean pathMayBeValid(File file) {
                return file == new File("/foo/bar") || file == new File("/foo/123")
            }
        }
    }

    def "supplies no installations for absent property"() {
        given:
        def supplier = new LocationListInstallationSupplier(providerFactory)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for empty property"() {
        given:
        currentGradlePropertyValue = ""

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single path"() {
        given:
        currentGradlePropertyValue = "/foo/bar"

        when:
        def directories = supplier.get()

        then:
        directories == [new File("/foo/bar")] as Set
    }

    def "uses org_gradle_java_installations_paths as source"() {
        currentGradlePropertyValue = "/foo/bar"

        when:
        def directories = supplier.get()

        then:
        directories == [new File("/foo/bar")] as Set
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        currentGradlePropertyValue = "/foo/bar,/foo/123"

        when:
        def directories = supplier.get()

        then:
        directories == [new File("/foo/bar"), new File("/foo/123")] as Set
    }

    @Unroll
    def "warns and filters for installations pointing to files, exists: #exists, directory: #directory"() {
        given:
        def supplier = new LocationListInstallationSupplier(providerFactory, logger)
        def file = Mock(File)
        file.exists() >> exists
        file.isDirectory() >> directory
        file.absolutePath >> path

        when:
        def pathIsValid = supplier.pathMayBeValid(file)

        then:
        pathIsValid == valid
        logger.toString() == logOutput

        where:
        path        | exists | directory | valid | logOutput
        '/foo/bar'  | true   | true      | true  | ''
        '/unknown'  | false  | null      | false | 'Directory \'/unknown\' used for java installations does not exist\n'
        '/foo/file' | true   | false     | false | 'Path for java installation \'/foo/file\' points to a file, not a directory\n'
    }

}
