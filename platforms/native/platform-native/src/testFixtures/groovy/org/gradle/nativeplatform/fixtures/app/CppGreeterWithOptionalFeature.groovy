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

import static org.gradle.nativeplatform.fixtures.app.SourceFileElement.ofFile

class CppGreeterWithOptionalFeature extends CppLibraryElement implements GreeterElement {
    final SourceFileElement header = ofFile(new SourceFile("headers", "greeter.h", """
#ifdef _WIN32
#define EXPORT_FUNC __declspec(dllexport)
#else
#define EXPORT_FUNC
#endif

class Greeter {
public:
    void EXPORT_FUNC sayHello();
};
"""))

    final SourceFileElement source = ofFile(new SourceFile("cpp", "greeter.cpp", """
#include <iostream>
#include "greeter.h"

void Greeter::sayHello() {
#ifdef WITH_FEATURE
#pragma message("compiling with feature enabled")
    std::cout << "${HelloWorldApp.HELLO_WORLD_FRENCH}" << std::endl;
#else
    std::cout << "${HelloWorldApp.HELLO_WORLD}" << std::endl;
#endif
}
"""))

    final SourceElement publicHeaders = header
    final SourceElement sources = source

    @Override
    String getExpectedOutput() {
        return "${HelloWorldApp.HELLO_WORLD}\n"
    }

    GreeterElement withFeatureEnabled() {
        return new GreeterElement() {
            @Override
            String getExpectedOutput() {
                return "${HelloWorldApp.HELLO_WORLD_FRENCH}\n"
            }
        }
    }
}
