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

class CppMultiply extends CppSourceFileElement implements MultiplyElement {
    final SourceFileElement header = ofFile(new SourceFile("headers", "multiply.h", """
#ifdef _WIN32
#define EXPORT_FUNC __declspec(dllexport)
#else
#define EXPORT_FUNC
#endif

class Multiply {
public:
    int EXPORT_FUNC multiply(int a, int b);
};
"""))

    final SourceFileElement source = ofFile(new SourceFile("cpp", "multiply.cpp", """
#include "multiply.h"

int Multiply::multiply(int a, int b) {
    return a * b;
}
"""))

    final SourceElement privateHeaders = header
    final SourceElement publicHeaders = empty()

    @Override
    int multiply(int a, int b) {
        return a * b
    }
}
