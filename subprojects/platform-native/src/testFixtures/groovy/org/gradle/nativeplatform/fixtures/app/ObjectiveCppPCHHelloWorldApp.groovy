/*
 * Copyright 2015 the original author or authors.
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

class ObjectiveCppPCHHelloWorldApp extends PCHHelloWorldApp {
    @Override
    SourceFile getMainSource() {
        return sourceFile("objcpp", "main.mm", """
            // Simple hello world app
            #define __STDC_LIMIT_MACROS
            #include <stdint.h>
            #import <Foundation/Foundation.h>
            #include "hello.h"

            int main (int argc, const char * argv[])
            {
                sayHello();
                printf("%d", sum(7, 5));
                return 0;
            }
        """);
    }

    @Override
    SourceFile getLibraryHeader() {
        getLibraryHeader("")
    }
    @Override
    SourceFile getLibraryHeader(String path) {
        return sourceFile("headers/${path}", "hello.h", """
            #ifndef HELLO_H
            #define HELLO_H
            void sayHello();
            int sum(int a, int b);
            #ifdef FRENCH
            #pragma message("<==== compiling bonjour.h ====>")
            #else
            #pragma message("<==== compiling hello.h ====>")
            #endif
            #endif
        """);
    }

    @Override
    TestApp getAlternate() {
        return new TestApp() {
            @Override
            SourceFile getMainSource() {
                return getAlternateMainSource()
            }

            @Override
            SourceFile getLibraryHeader() {
                return sourceFile("headers", "hello.h", """
                    #ifndef HELLO_H
                    #define HELLO_H
                    void sayHello();
                    int sum(int a, int b);

                    #pragma message("<==== compiling althello.h ====>")
                    #endif
                """);
            }

            @Override
            List<SourceFile> getLibrarySources() {
                return getAlternateLibrarySources()
            }
        }
    }

    @Override
    List<SourceFile> getLibrarySources() {
        return getLibrarySources("")
    }

    @Override
    List<SourceFile> getLibrarySources(String path) {
        return [
                sourceFile("objcpp", "hello.mm", """
                    #define __STDC_LIMIT_MACROS
                    #include "${path}hello.h"
                    #include <iostream>
                    #include <stdint.h>
                    #import <Foundation/Foundation.h>

                    #ifdef FRENCH
                    const char* greeting() {
                        return "${HELLO_WORLD_FRENCH}";
                    }
                    #endif

                    void sayHello() {
                        #ifdef FRENCH
                        std::cout << greeting() << std::endl;
                        #else
                        NSString *helloWorld = @"${HELLO_WORLD}\\n";
                        NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
                        NSData *strData = [helloWorld dataUsingEncoding: NSASCIIStringEncoding];
                        [stdout writeData: strData];
                        #endif
                    }
                """),
                sourceFile("objcpp", "sum.mm", """
                    #include "${path}hello.h"
                    int sum(int a, int b) {
                        return a + b;
                    }
                """)
        ]
    }

    @Override
    SourceFile getSystemHeader() {
        return getSystemHeader("")
    }

    @Override
    SourceFile getSystemHeader(String path) {
        sourceFile("headers/${path}", "systemHeader.h", """
            #ifndef SYSTEMHEADER_H
            #define SYSTEMHEADER_H
            void systemCall();
            #pragma message("<==== compiling systemHeader.h ====>")
            #endif
        """)
    }

    @Override
    String getIOHeader() {
        return "iostream"
    }

    @Override
    SourceFile getAlternateMainSource() {
        return getMainSource()
    }

    @Override
    String getAlternateOutput() {
        return null
    }

    @Override
    List<SourceFile> getAlternateLibrarySources() {
        return getLibrarySources()
    }

    @Override
    String getAlternateLibraryOutput() {
        return null
    }

    public String getExtraConfiguration(String binaryName = null) {
        return """
            binaries.matching { ${binaryName ? "it.name == '$binaryName'" : "true"} }.all {
                if (targetPlatform.operatingSystem.macOsX) {
                    linker.args "-framework", "Foundation"
                } else {
                    objcppCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                    linker.args "-lgnustep-base", "-lobjc"
                }
            }
        """
    }

    @Override
    List<String> getPluginList() {
        ['objective-cpp']
    }
}
