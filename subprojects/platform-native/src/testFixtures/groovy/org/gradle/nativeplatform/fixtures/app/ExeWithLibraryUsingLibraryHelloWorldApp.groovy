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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.test.fixtures.file.TestFile

class ExeWithLibraryUsingLibraryHelloWorldApp extends HelloWorldApp {

    void writeSources(TestFile mainSourceDir, TestFile librarySourceDir, TestFile greetingsLibrarySourceDir) {
        getExecutable().writeSources(mainSourceDir)
        getLibrary().writeSources(librarySourceDir)
        getGreetingsHeader().writeToDir(greetingsLibrarySourceDir)
        for (SourceFile sourceFile : greetingsSources) {
            sourceFile.writeToDir(greetingsLibrarySourceDir)
        }
    }


    TestNativeComponent getGreetingsLibrary() {
        return new TestNativeComponent() {
            @Override
            List<SourceFile> getHeaderFiles() {
                return Arrays.asList(getGreetingsHeader())
            }

            @Override
            List<SourceFile> getSourceFiles() {
                return greetingsSources
            }
        }
    }

    @Override
    String getEnglishOutput() {
        return HELLO_WORLD + " " + HELLO_WORLD
    }

    @Override
    String getFrenchOutput() {
        return HELLO_WORLD_FRENCH + "\n"
    }

    @Override
    SourceFile getMainSource() {
        sourceFile("cpp", "main.cpp", """
            #include <iostream>
            #include "hello.h"

            const char* getExeHello() {
                #ifdef FRENCH
                return "${HELLO_WORLD_FRENCH}";
                #else
                return "${HELLO_WORLD}";
                #endif
            }

            int main () {
                std::cout << getExeHello() << " ";
                sayHello();
                return 0;
            }
        """)
    }

    @Override
    SourceFile getLibraryHeader() {
        sourceFile("headers", "hello.h", """
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC sayHello();
        """)
    }

    List<SourceFile> librarySources = [
        new SourceFile("cpp", "hello.cpp", """
            #include <iostream>
            #include "hello.h"
            #include "greetings.h"

            void DLL_FUNC sayHello() {
                std::cout << getHello();
            }
        """)
    ]

    SourceFile getGreetingsHeader() {
        sourceFile("headers", "greetings.h", """
            #include <string>

            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            std::string DLL_FUNC getHello();
        """)
    }

    List<SourceFile> greetingsSources = [
        sourceFile("cpp", "greetings.cpp", """
            #include "greetings.h"

            std::string DLL_FUNC getHello() {
                #ifdef FRENCH
                return "${HELLO_WORLD_FRENCH}";
                #else
                return "${HELLO_WORLD}";
                #endif
            }
        """)
    ]

}
