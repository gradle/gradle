/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.AbstractNativePreCompiledHeaderIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class CppPreCompiledHeaderSourcesIntegrationTest extends AbstractNativePreCompiledHeaderIntegrationTest implements DirectoryBuildCacheFixture {

    @Override
    IncrementalHelloWorldApp getApp() {
        return new CppHelloWorldApp()
    }

    @ToBeFixedForConfigurationCache
    def "caching is disabled if precompiled headers are configured" () {
        writeStandardSourceFiles()

        when:
        buildFile << preCompiledHeaderComponent()
        withBuildCache().run "helloSharedLibrary", "--info"

        then:
        libAndPCHTasksExecuted()
        pchCompiledOnceForEach([ PCHHeaderDirName ])
        output.contains "Caching disabled for task ':compileHelloSharedLibraryCppPreCompiledHeader' because:\n" +
            "  Not made cacheable, yet"
        output.contains "Caching disabled for task ':compileHelloSharedLibraryHelloCpp' because:\n" +
            "  'Pre-compiled headers are used' satisfied"
    }

    @Requires(UnitTestPreconditions.MacOs)
    @ToBeFixedForConfigurationCache
    def "can compile and link C++ code with precompiled headers using standard macOS framework" () {
        given:
        writeStandardSourceFiles()

        and:
        file(commonHeader.withPath("src/hello")) << """
            #include <CoreFoundation/CoreFoundation.h>
        """

        and:
        file("src/hello/cpp/includeFramework.cpp") << """
            #include "common.h"
            void sayHelloFoundation() {
                CFShow(CFSTR("Hello"));
            }
        """

        and:
        buildFile << preCompiledHeaderComponent()
        buildFile << """
            model {
                components {
                    hello {
                        binaries.withType(SharedLibraryBinarySpec) {
                            linker.args "-framework", "CoreFoundation"
                        }
                    }
                }
            }
        """

        expect:
        succeeds "helloSharedLibrary"
        libAndPCHTasksExecuted()
    }
}
