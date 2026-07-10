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


import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class EnvironmentVariableListInstallationSupplierTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    final jdk8 = tmpDir.createDir("jdk8")
    final jdk9 = tmpDir.createDir("jdk9")
    final buildOptions = Mock(ToolchainConfiguration) {
        getEnvironmentVariableValue("JDK8") >> jdk8.absolutePath
        getEnvironmentVariableValue("JDK9") >> jdk9.absolutePath
    }

    @Subject
    def supplier =  new EnvironmentVariableListInstallationSupplier(buildOptions, new IdentityFileResolver())

    def "supplies no installations for empty property"() {
        when:
        buildOptions.getJavaInstallationsFromEnvironment() >> []
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single path"() {
        when:
        buildOptions.getJavaInstallationsFromEnvironment() >> ["JDK8"]
        def directories = supplier.get()

        then:
        directories.size() == 1
        directories[0].location == jdk8
        directories[0].source == "environment variable 'JDK8'"
    }

    def "supplies multiple installations for multiple paths"() {
        when:
        buildOptions.getJavaInstallationsFromEnvironment() >> ["JDK8", "JDK9"]
        def directories = consistentOrder(supplier.get())

        then:
        directories.size() == 2
        directories[0].location == jdk8
        directories[0].source == "environment variable 'JDK8'"
        directories[1].location == jdk9
        directories[1].source == "environment variable 'JDK9'"
    }

    Set<InstallationLocation> consistentOrder(Set<InstallationLocation> s) {
        s.sort { it.location.absolutePath }
    }
}
