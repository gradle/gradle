/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile;

public class ExeWithLibraryUsingSwiftLibraryHelloWorldApp extends HelloWorldApp {

    void writeSources(TestFile mainSourceDir, TestFile librarySourceDir, TestFile greetingsLibrarySourceDir) {
        getExecutable().writeSources(mainSourceDir)
        getLibrary().writeSources(librarySourceDir)
        getGreetingsHeader().writeToDir(greetingsLibrarySourceDir);
        for (SourceFile sourceFile : greetingsSources) {
            sourceFile.writeToDir(greetingsLibrarySourceDir);
        }
    }


    public TestNativeComponent getGreetingsLibrary() {
        return new TestNativeComponent() {
            @Override
            public List<SourceFile> getHeaderFiles() {
                return Arrays.asList(getGreetingsHeader())
            }

            @Override
            public List<SourceFile> getSourceFiles() {
                return greetingsSources
            }
        };
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
        sourceFile("swift", "main.swift", """
            import Hello

            func getExeHello() -> String {
                #if FRENCH
                return "${HELLO_WORLD_FRENCH}"
                #else
                return "${HELLO_WORLD}"
                #endif
            }

            func main() -> Int {
                print(getExeHello() + " ", terminator:"")
                sayHello()
                return 0
            }

            _ = main()
        """)
    }

    @Override
    SourceFile getLibraryHeader() {
        sourceFile("headers", "hello.h", "");
    }

    List<SourceFile> librarySources = [
        sourceFile("swift", "hello.swift", """
            import Greeting

            public func sayHello() {
                print(getHello(), terminator:"")
            }
        """)
    ]

    SourceFile getGreetingsHeader() {
        sourceFile("headers", "greetings.h", "");
    }

    List<SourceFile> greetingsSources = [
        sourceFile("swift", "greetings.swift", """
            public func getHello() -> String{
                #if FRENCH
                return "${HELLO_WORLD_FRENCH}"
                #else
                return "${HELLO_WORLD}"
                #endif
            }
        """)
    ]

}
