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

class EnvironmentVariableListInstallationSupplierTest extends Specification {

    def "supplies no installations for absent property"() {
        given:
        def supplier = new EnvironmentVariableListInstallationSupplier(createProviderFactory())

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for empty property"() {
        given:
        def supplier = createSupplier(null)

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
        directories == [new File("/path/jdk8")] as Set
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        def supplier = createSupplier("JDK8,JDK9")

        when:
        def directories = supplier.get()

        then:
        directories == [new File("/path/jdk8"), new File("/path/jdk9")] as Set
    }

    @Unroll
    def "warns and filters for installations pointing to files, exists: #exists, directory: #directory"() {
        given:
        def logger = new ToStringLogger()
        def supplier = EnvironmentVariableListInstallationSupplier.withLogger(createProviderFactory(), logger)
        def file = Mock(File)
        file.exists() >> exists
        file.isDirectory() >> directory
        file.absolutePath >> path

        when:
        def pathIsValid = supplier.pathMayBeValid(file, "ENVX")

        then:
        pathIsValid == valid
        logger.toString() == logOutput

        where:
        path        | exists | directory | valid | logOutput
        '/foo/bar'  | true   | true      | true  | ''
        '/unknown'  | false  | null      | false | 'Directory \'/unknown\' (from environment variable \'ENVX\') used for java installations does not exist\n'
        '/foo/file' | true   | false     | false | 'Path for java installation \'/foo/file\' (from environment variable \'ENVX\') points to a file, not a directory\n'
    }

    private ProviderFactory createProviderFactory(String propertyValue) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.fromEnv") >> mockProvider(propertyValue)
        providerFactory.environmentVariable("JDK8") >> mockProvider("/path/jdk8")
        providerFactory.environmentVariable("JDK9") >> mockProvider("/path/jdk9")
        providerFactory
    }

    private Provider<String> mockProvider(String value) {
        def provider = Mock(Provider)
        provider.get() >> value
        provider.isPresent() >> { value != null }
        provider.forUseAtConfigurationTime() >> provider
        provider
    }

    private EnvironmentVariableListInstallationSupplier createSupplier(String propertyValue) {
        new EnvironmentVariableListInstallationSupplier(createProviderFactory(propertyValue)) {
            @Override
            boolean pathMayBeValid(File file, String envVariableName) {
                return file == new File("/path/jdk8") || file == new File("/path/jdk9")
            }
        }
    }

}
