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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

class MixedObjectiveCHelloWorldApp extends HelloWorldApp {

    List pluginList = ["objective-c", "objective-cpp", "c", "cpp"]

    String getExtraConfiguration(String binaryName = null) {
        return """
            model {
                binaries {
                    ${binaryName ? binaryName : "all"} {
                        if (targetPlatform.operatingSystem.macOsX) {
                            linker.args "-framework", "Foundation"
                        } else {
                            objcCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                            objcppCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                            linker.args "-lgnustep-base", "-lobjc"
                        }
                    }
                }
            }
        """
    }

    @Override
    SourceFile getMainSource() {
        return sourceFile("objc", "main.m", """
            // Simple hello world app
            #import "hello.h"
            #include <stdio.h>

            int main (int argc, const char * argv[]) {
                doGreeting();
                printf("%d", sum(7, 5));
                return 0;
            }

        """)
    }

    @Override
    SourceFile getLibraryHeader() {
        return sourceFile("headers", "hello.h", """
            #ifdef __cplusplus
            extern "C" {
            #endif
            int sum(int a, int b);
            void world();
            #ifdef __cplusplus
            }
            #endif
            void doGreeting();
            void hello();
            void bonjour();
           """)
    }

    @Override
    List<SourceFile> getLibrarySources() {
        return [
                sourceFile("objc", "objclib.c", """
            #import <Foundation/Foundation.h>
            #include <stdio.h>
            #include "hello.h"

            void doGreeting()
            {
                #ifdef FRENCH
                bonjour();
                #else
                hello();
                world();
                #endif
            }

            void hello()
            {
                NSString *hello = @"Hello";
                NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
                NSData *strData = [hello dataUsingEncoding: NSASCIIStringEncoding];
                [stdout writeData: strData];
            }"""),

                sourceFile("c", "clib.c", """
            #include <stdio.h>
            #include "hello.h"

            void bonjour() {
                printf("Bonjour, Monde!\\n");
            }
        """),
                sourceFile("cpp", "cpplib.cpp", """
            #include "hello.h"
            #include <iostream>
            using namespace std;

            void world() {
                cout << ", World!" << std::endl;
            }
        """),
                sourceFile("objcpp", "sum.mm", """
            #import "hello.h"
            int sum(int a, int b) {
                return a + b;
            }
            """)
        ]
    }
}
