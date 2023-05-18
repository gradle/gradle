/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires([
    UnitTestPreconditions.NotWindows,
    UnitTestPreconditions.NotMacOs
])
class SharedLibrarySoNameIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "setup"() {
        settingsFile << "rootProject.name = 'test'"

        def app = new CppHelloWorldApp()
        app.library.writeSources(file("src/hello"))

        buildFile << """
apply plugin: 'cpp'
model {
    components {
        hello(NativeLibrarySpec)
    }
}
"""
    }

    @ToBeFixedForConfigurationCache
    def "library soname is file name when installName is not set"() {
        when:
        succeeds "helloSharedLibrary"

        then:
        final sharedLibrary = sharedLibrary("build/libs/hello/shared/hello")
        sharedLibrary.soName == sharedLibrary.file.name
    }

    @ToBeFixedForConfigurationCache
    def "library soname uses specified installName"() {
        given:
        buildFile << """
tasks.withType(LinkSharedLibrary) {
    it.installName = 'hello-install-name'
}
"""

        when:
        succeeds "helloSharedLibrary"

        then:
        sharedLibrary("build/libs/hello/shared/hello").soName == "hello-install-name"
    }

    @ToBeFixedForConfigurationCache
    def "library soname defaults when installName is null"() {
        given:
        buildFile << """
tasks.withType(LinkSharedLibrary) {
    it.installName = null
}
"""

        when:
        succeeds "helloSharedLibrary"

        then:
        final library = sharedLibrary("build/libs/hello/shared/hello")
        def expectedSoName = OperatingSystem.current().macOsX ? library.file.absolutePath : null
        library.soName == expectedSoName
    }
}
