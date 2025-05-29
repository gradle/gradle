/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class EnvironmentVariableJavaHomeInstallationSupplierTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "supplies no installations for not defined or empty JAVA_HOME property"() {
        when:
        def buildOptions = new DefaultToolchainConfiguration(environment)
        def supplier = new EnvironmentVariableJavaHomeInstallationSupplier(buildOptions)
        def directories = supplier.get()

        then:
        directories.isEmpty()

        where:
        environment << [[:], [JAVA_HOME: ''], [OTHER: 'other']]
    }

    def "supplies installation for JAVA_HOME property"() {
        given:
        def jdk8 = tmpDir.createDir("jdk8")
        def buildOptions = new DefaultToolchainConfiguration(["JAVA_HOME": jdk8.absolutePath])

        when:
        def supplier = new EnvironmentVariableJavaHomeInstallationSupplier(buildOptions)
        def directories = supplier.get()

        then:
        directories.size() == 1
        directories[0].location == jdk8
        directories[0].source == "environment variable 'JAVA_HOME'"
    }
}
