/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativebinaries.language.cpp

import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.SourceFile
import org.gradle.nativebinaries.language.cpp.fixtures.app.TestComponent

// TODO add coverage for asm, windows-resources, objective-c/c++ & mixed sources
class DuplicateBaseNamesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def "can have sourcefiles with same base name but different directories"() {
        setup:
        testApp.writeSources(file("src/main"))
        buildFile << """
            apply plugin: '$sourceType'
            executables {
                main {}
            }
        """
        expect:
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == "foo1foo2"
        where:
        sourceType |  testApp
        "c"        |  new DuplicateCBaseNamesTestApp()
        "cpp"      |  new DuplicateCppBaseNamesTestApp()
    }
}

class DuplicateCBaseNamesTestApp extends TestComponent {
    @Override
    List<SourceFile> getSourceFiles() {
        [sourceFile("c", "main.c", """
            #include <stdio.h>
            #include "foo1/foo.h"
            #include "foo2/foo.h"
            int main () {
               foo1();
               foo2();
               return 0;
            }
        """),
                sourceFile("c/foo1", "foo.c", """
            #include <stdio.h>
            #include "foo.h"

            void DLL_FUNC foo1() {
                printf("foo1");
            }
        """),

                sourceFile("c/foo2", "foo.c", """
            #include <stdio.h>
            #include "foo.h"

            void DLL_FUNC foo2() {
                printf("foo2");
            }
        """)]
    }

    @Override
    List<SourceFile> getHeaderFiles() {
        [sourceFile("c/foo1", "foo.h", """
           #ifdef _WIN32
           #define DLL_FUNC __declspec(dllexport)
           #else
           #define DLL_FUNC
           #endif
           void DLL_FUNC foo1();
           """),
                sourceFile("c/foo2", "foo.h", """
           #ifdef _WIN32
           #define DLL_FUNC __declspec(dllexport)
           #else
           #define DLL_FUNC
           #endif
           void DLL_FUNC foo2();
           """)]
    }
}

class DuplicateCppBaseNamesTestApp extends TestComponent {
    @Override
    List<SourceFile> getSourceFiles() {
        [sourceFile("cpp", "main.cpp", """
            #include <iostream>
            #include "foo1/foo.h"
            #include "foo2/foo.h"
            using namespace std;
            int main () {
               foo1();
               foo2();
               return 0;
            }
        """),
                sourceFile("cpp/foo1", "foo.cpp", """
            #include <iostream>
            #include "foo.h"
            using namespace std;

            void DLL_FUNC foo1() {
                cout << "foo1";
            }
        """),

                sourceFile("cpp/foo2", "foo.cpp", """
            #include <iostream>
            #include "foo.h"
            using namespace std;

            void DLL_FUNC foo2() {
                cout << "foo2";
            }
        """)]
    }

    @Override
    List<SourceFile> getHeaderFiles() {
        [sourceFile("cpp/foo1", "foo.h", """
           #ifdef _WIN32
           #define DLL_FUNC __declspec(dllexport)
           #else
           #define DLL_FUNC
           #endif
           void DLL_FUNC foo1();
           """),
                sourceFile("cpp/foo2", "foo.h", """
           #ifdef _WIN32
           #define DLL_FUNC __declspec(dllexport)
           #else
           #define DLL_FUNC
           #endif
           void DLL_FUNC foo2();
           """)]
    }
}