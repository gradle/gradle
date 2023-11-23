/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec

class CppBinariesRelocationIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements CppTaskNames {
    def setup() {
        createDirs("lib")
        settingsFile << """
            rootProject.name = 'test'
            include 'lib'
        """

        buildFile << """
            apply plugin: 'cpp-application'
            application { c ->
                c.dependencies {
                    implementation project(':lib')
                }
            }
            project(':lib') {
                apply plugin: 'cpp-library'
            }
        """

        file("lib/src/main/public/lib.h") << libraryHeader
        file("lib/src/main/cpp/lib.cpp") << librarySource
        file("src/main/cpp/app.cpp") << applicationSource
    }

    private static String getLibraryHeader() {
        return """
            #ifdef _WIN32
            #define EXPORT_FUNC __declspec(dllexport)
            #else
            #define EXPORT_FUNC
            #endif

            void EXPORT_FUNC lib_func();
        """
    }

    private static String getLibrarySource() {
        return """
            #include "lib.h"
            void lib_func() { }
        """
    }

    private static String getApplicationSource() {
        return """
            #include "lib.h"
            int main() {
                lib_func();
                return 0;
            }
        """
    }

    @ToBeFixedForConfigurationCache
    def "can execute application with dependencies when relocated"() {
        def installDir = file("build/install/main/debug")
        def relocatedInstallDir = file("relocated-install")

        when:
        succeeds("installDebug")

        then:
        def install = installation(installDir)
        install.assertInstalled()
        install.exec()

        when:
        installDir.copyTo(relocatedInstallDir)

        then:
        def relocatedInstall = installation(relocatedInstallDir)
        relocatedInstall.assertInstalled()
        relocatedInstall.exec()

        when:
        succeeds("clean")

        then:
        install.assertNotInstalled()

        and:
        relocatedInstall.assertInstalled()
        relocatedInstall.exec()
    }
}
