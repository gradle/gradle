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

class SwiftHelloWorldApp extends IncrementalHelloWorldApp {
    @Override
    SourceFile getMainSource() {
        return sourceFile("swift", "main.swift", """
            // Simple hello world app
            func main() -> Int {
              let greeter = Greeter()
              greeter.sayHello()
              print(sum(a: 5, b: 7))
              return 0
            }

            _ = main()
        """);
    }

    SourceFile getAlternateMainSource() {
        sourceFile("swift", "main.swift", """
            func main() -> Int {
              let greeter = Greater()
              greeter.sayHello()
              return 0
            }

            _ = main()
        """)
    }

    String alternateOutput = "$HELLO_WORLD\n"

    @Override
    SourceFile getLibraryHeader() {
        return sourceFile("headers", "hello.h", """
            #ifndef HELLO_H
            #define HELLO_H
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            class Greeter {
                public:
                void DLL_FUNC sayHello();
            };

            int DLL_FUNC sum(int a, int b);

            #ifdef FRENCH
            #pragma message("<==== compiling bonjour.h ====>")
            #else
            #pragma message("<==== compiling hello.h ====>")
            #endif

            #endif
        """);
    }

    @Override
    def SourceFile getCommonHeader() {
        sourceFile("headers", "common.h", """
            #ifndef COMMON_H
            #define COMMON_H
            #include "hello.h"
            #include <iostream>
            #endif
        """)
    }

    List<SourceFile> librarySources = [
        sourceFile("swift", "hello.swift", """
            #if FRENCH
            func greeting() -> String {
                return "${HELLO_WORLD_FRENCH}"
            }
            #endif


            public class Greeter {
                public func sayHello() {
                    #if FRENCH
                    print(greeting())
                    #else
                    print("${HELLO_WORLD}")
                    #endif
                }
            }
        """),
        sourceFile("swift", "sum.swift", """
            public func sum(a: Int, b: Int) -> Int {
                return a + b
            }
        """)
    ]

    List<SourceFile> alternateLibrarySources = [
        sourceFile("cpp", "hello.cpp", """
            #include "common.h"

            void DLL_FUNC Greeter::sayHello() {
                std::cout << "[${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}]" << std::endl;
            }

            // Extra function to ensure library has different size
            int anotherFunction() {
                return 1000;
            }
        """),
        sourceFile("cpp", "sum.cpp", """
            #include "common.h"

            int DLL_FUNC sum(int a, int b) {
                return a + b;
            }
        """)
    ]

    String alternateLibraryOutput = "[${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}]\n12"

    TestNativeComponent getGoogleTestTests() {
        return new TestNativeComponent() {
            List<SourceFile> sourceFiles = [
                sourceFile("cpp", "test.cpp", """
#include "gtest/gtest.h"
#include "hello.h"

using namespace testing;

TEST(HelloTest, test_sum) {
  ASSERT_TRUE(sum(0, 2) == 2);
#ifndef ONE_TEST
  ASSERT_TRUE(sum(0, -2) == -2);
  ASSERT_TRUE(sum(2, 2) == 4);
#endif
}

int main(int argc, char **argv) {
  testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
                    """),
            ]
            List<SourceFile> headerFiles = [
            ]
        };
    }

    public SourceFile getBrokenFile() {
        return sourceFile("swift", "broken.swift", """'broken""")
    }

    @Override
    String getSourceSetType() {
        return "SwiftSourceSet"
    }
}
