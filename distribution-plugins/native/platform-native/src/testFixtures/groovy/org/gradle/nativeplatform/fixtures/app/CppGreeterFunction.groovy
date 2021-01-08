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

import static org.gradle.nativeplatform.fixtures.app.SourceFileElement.ofFile

class CppGreeterFunction extends CppLibraryElement implements GreeterElement {
    final SourceFileElement header
    final SourceFileElement privateHeader
    final SourceFileElement source

    SourceElement getPublicHeaders() {
        return header
    }

    SourceElement getPrivateHeaders() {
        return privateHeader
    }

    SourceElement getSources() {
        return source
    }

    CppGreeterFunction(String publicHeaderDir = "headers") {
        header = ofFile(sourceFile(publicHeaderDir, "greeter.h", """
#ifndef GREETER_H
#define GREETER_H

#ifdef _WIN32
#define EXPORT_FUNC __declspec(dllexport)
#else
#define EXPORT_FUNC
#endif

#ifdef __cplusplus
extern "C" {
#endif

void sayGreeting();

#ifdef __cplusplus
}
#endif

#define DUMMY_STRING "dummy"
extern const char* PUBLIC_DUMMY_STRING;

#endif // GREETER_H
"""))

        privateHeader = ofFile(sourceFile("headers", "greeter_consts.h", """
#ifndef GREETER_CONSTS_H
#define GREETER_CONSTS_H

#define GREETING "${HelloWorldApp.HELLO_WORLD}"

#endif // GREETER_CONSTS_H
"""))

        source = ofFile(sourceFile("cpp", "greeter.cpp", """
#include <iostream>
#include "greeter.h"
#include "greeter_consts.h"

void sayGreeting() {
    std::cout << GREETING << std::endl;
}

const char* PUBLIC_DUMMY_STRING = DUMMY_STRING;
"""))
    }

    @Override
    CppGreeterFunction asLib() {
        return new CppGreeterFunction("public")
    }

    @Override
    String getExpectedOutput() {
        return "${HelloWorldApp.HELLO_WORLD}\n"
    }
}
