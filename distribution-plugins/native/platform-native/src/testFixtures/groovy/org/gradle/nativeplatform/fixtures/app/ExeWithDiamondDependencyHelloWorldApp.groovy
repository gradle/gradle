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

class ExeWithDiamondDependencyHelloWorldApp extends ExeWithLibraryUsingLibraryHelloWorldApp {

    @Override
    String getEnglishOutput() {
        return HELLO_WORLD + " " + HELLO_WORLD + " " + HELLO_WORLD
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
            #include "greetings.h"

            const char* getExeHello() {
                #ifdef FRENCH
                return "${HELLO_WORLD_FRENCH}";
                #else
                return "${HELLO_WORLD}";
                #endif
            }

            int main () {
                std::cout << getExeHello() << " ";
                std::cout << getHello() << " ";
                sayHello();
                return 0;
            }
        """)
    }
}
