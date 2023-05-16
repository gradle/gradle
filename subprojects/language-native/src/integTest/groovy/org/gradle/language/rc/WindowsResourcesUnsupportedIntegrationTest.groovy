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
package org.gradle.language.rc

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class WindowsResourcesUnsupportedIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    HelloWorldApp helloWorldApp = new CppHelloWorldApp()

    @Requires(UnitTestPreconditions.NotWindows)
    @ToBeFixedForConfigurationCache
    def "resource files are ignored on unsupported platforms"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
    id 'windows-resources'
}

model {
    components {
        main(NativeExecutableSpec)
    }
}
         """

        and:
        helloWorldApp.writeSources(file("src/main"))
        file("src/main/rc/broken.rc") << """
        #include <stdio.h>

        NOT A VALID RESOURCE
"""

        when:
        run "mainExecutable"

        then:
        notExecuted(":compileMainExecutableMainRc")
    }

    @Requires(UnitTestPreconditions.Windows)
    @RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
    def "reasonable error message when attempting to compile resource files with unsupported tool chain"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
    id 'windows-resources'
}

model {
    components {
        main(NativeExecutableSpec)
    }
}
         """

        and:
        helloWorldApp.writeSources(file("src/main"))
        file("src/main/rc/broken.rc") << """
        #include <stdio.h>

        NOT A VALID RESOURCE
"""

        when:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainRc'.")
        failure.assertHasCause("Windows resource compiler is not available")
    }
}

