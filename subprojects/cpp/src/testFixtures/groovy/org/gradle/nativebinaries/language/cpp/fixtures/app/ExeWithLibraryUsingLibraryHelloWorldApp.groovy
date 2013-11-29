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

package org.gradle.nativebinaries.language.cpp.fixtures.app

import org.gradle.test.fixtures.file.TestFile;

public class ExeWithLibraryUsingLibraryHelloWorldApp extends HelloWorldApp {

    void writeSources(TestFile mainSourceDir, TestFile librarySourceDir, TestFile greetingsLibrarySourceDir) {
        getExecutable().writeSources(mainSourceDir)
        getLibrary().writeSources(librarySourceDir)
        getGreetingsHeader().writeToDir(greetingsLibrarySourceDir);
        for (SourceFile sourceFile : greetingsSources) {
            sourceFile.writeToDir(greetingsLibrarySourceDir);
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
        """);
    }

    List<SourceFile> librarySources = [
        sourceFile("cpp", "hello.cpp", """
            #include <iostream>
            #include "hello.h"
            #include "greetings.h"

            void DLL_FUNC sayHello() {
                const char* greeting = getHello();
                std::cout << greeting;
            }
        """)
    ]

    SourceFile getGreetingsHeader() {
        sourceFile("headers", "greetings.h", """
            const char* getHello();
        """);
    }

    List<SourceFile> greetingsSources = [
        sourceFile("cpp", "greetings.cpp", """
            #include "greetings.h"

            const char* getHello() {
                #ifdef FRENCH
                return "${HELLO_WORLD_FRENCH}";
                #else
                return "${HELLO_WORLD}";
                #endif
            }
        """)
    ]

}
