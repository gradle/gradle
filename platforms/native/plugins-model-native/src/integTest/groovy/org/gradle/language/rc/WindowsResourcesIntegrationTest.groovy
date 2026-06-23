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

import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.AbstractNativeLanguageIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.nativeplatform.fixtures.app.WindowsResourceHelloWorldApp
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultWindowsSdkLocator
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkInstall
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkLocator
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.internal.TextUtil

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP
import static org.gradle.util.Matchers.containsText

@RequiresInstalledToolChain(VISUALCPP)
class WindowsResourcesIntegrationTest extends AbstractNativeLanguageIntegrationTest {
    static final List<WindowsSdkInstall> NON_DEFAULT_SDKS = getNonDefaultSdks()
    HelloWorldApp helloWorldApp = new WindowsResourceHelloWorldApp()

    def "compile and link executable with #sdk.name (#sdk.version.toString()) [#tc.displayName]"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }

                toolChains {
                    ${toolChain.id} {
                        windowsSdkDir = "${TextUtil.normaliseFileSeparators(sdk.getBaseDir().absolutePath)}"
                    }
                }
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput

        where:
        sdk << NON_DEFAULT_SDKS
        tc = toolChain
    }

    def "user receives a reasonable error message when resource compilation fails"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec)
    }
}
         """

        and:
        file("src/main/rc/broken.rc") << """
        #include <stdio.h>

        NOT A VALID RESOURCE
"""

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainRc'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Windows resource compiler failed while compiling broken.rc"))
    }

    def "can create resources-only shared library"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: "resources"
            }
        }
        resources(NativeLibrarySpec) {
            binaries.all {
                linker.args "/noentry", "/machine:x86"
            }
        }
    }
}
"""

        and:
        file("src/resources/rc/resources.rc") << """
            #include "resources.h"

            STRINGTABLE
            {
                IDS_HELLO, "Hello!"
            }
        """

        and:
        file("src/resources/headers/resources.h") << """
            #define IDS_HELLO    111
        """

        and:
        file("src/main/cpp/main.cpp") << """
            #include <iostream>
            #include <windows.h>
            #include <string>
            #include "resources.h"

            std::string LoadStringFromResource(UINT stringID)
            {
                HMODULE instance = LoadLibraryEx("resources.dll", NULL, LOAD_LIBRARY_AS_DATAFILE);
                WCHAR * pBuf = NULL;
                int len = LoadStringW(instance, stringID, reinterpret_cast<LPWSTR>(&pBuf), 0);
                std::wstring wide = std::wstring(pBuf, len);
                return std::string(wide.begin(), wide.end());
            }

            int main() {
                std::string hello = LoadStringFromResource(IDS_HELLO);
                std::cout << hello;
                return 0;
            }
        """

        when:
        run "installMainExecutable"

        then:
        resourceOnlyLibrary("build/libs/resources/shared/resources").assertExists()
        installation("build/install/main").exec().out == "Hello!"
    }

    static List<WindowsSdkInstall> getNonDefaultSdks() {
        WindowsSdkLocator locator = new DefaultWindowsSdkLocator(OperatingSystem.current(), NativeServicesTestFixture.getInstance().get(WindowsRegistry.class), NativeServicesTestFixture.getInstance().get(SystemInfo.class))
        WindowsSdkInstall defaultSdk = locator.locateComponent(null).component
        return locator.locateAllComponents() - defaultSdk
    }
}
