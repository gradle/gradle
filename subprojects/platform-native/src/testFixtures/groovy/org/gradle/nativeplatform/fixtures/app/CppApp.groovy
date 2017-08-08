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

/**
 * A single module C++ app, with several source files.
 */
class CppApp extends SourceElement implements AppElement {
    final greeterHeader = new SingleSourceFileElement() {
        @Override
        SourceFile getSourceFile() {
            return sourceFile("headers", "greeter.h", """
class Greeter {
public:
    void sayHello();
};
""")
        }
    }
    final greeter = new SingleSourceFileElement() {
        @Override
        SourceFile getSourceFile() {
            return sourceFile("cpp", "greeter.cpp", """
#include <iostream>
#include "greeter.h"

void Greeter::sayHello() {
    std::cout << "${HelloWorldApp.HELLO_WORLD}" << std::endl;
}
""")
        }
    }
    final sumHeader = new SingleSourceFileElement() {
        @Override
        SourceFile getSourceFile() {
            return sourceFile("headers", "sum.h", """
class Sum {
public:
    int sum(int a, int b);
};
""")
        }
    }
    final sum = new SingleSourceFileElement() {
        @Override
        SourceFile getSourceFile() {
            return sourceFile("cpp", "sum.cpp", """
#include "sum.h"

int Sum::sum(int a, int b) {
    return a + b;
}
""")
        }
    }
    final main = new SingleSourceFileElement() {
        @Override
        SourceFile getSourceFile() {
            return sourceFile("cpp", "main.cpp", """
#include <iostream>
#include "sum.h"
#include "greeter.h"

int main(int argc, char** argv) {
    Greeter greeter;
    greeter.sayHello();
    Sum sum;
    std::cout << sum.sum(5, 7) << std::endl;
    return 0;
}
""")
        }
    }
    final List<SourceFile> files = [main.sourceFile, greeterHeader.sourceFile, greeter.sourceFile, sumHeader.sourceFile, sum.sourceFile]

    SourceElement getHeaders() {
        return new SourceElement() {
            @Override
            List<SourceFile> getFiles() {
                return [greeterHeader.sourceFile, sumHeader.sourceFile]
            }
        }
    }

    @Override
    String getExpectedOutput() {
        return "${HelloWorldApp.HELLO_WORLD}\n12\n"
    }
}
