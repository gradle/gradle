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

import org.gradle.internal.os.OperatingSystem


class ObjectiveCppHelloWorldApp extends IncrementalHelloWorldApp {
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
        return sourceFile("headers", "hello.h", """
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC sayHello();
            int DLL_FUNC sum(int a, int b);
        """);
    }

    List<SourceFile> librarySources = [
            sourceFile("objcpp", "hello.mm", """
            #define __STDC_LIMIT_MACROS
            #include <stdint.h>
            #include "hello.h"
            #import <Foundation/Foundation.h>

            #include <iostream>

            #ifdef FRENCH
            const char* greeting() {
                return "${HELLO_WORLD_FRENCH}";
            }
            #endif

            void DLL_FUNC sayHello() {
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
            #include "hello.h"
            int DLL_FUNC sum(int a, int b) {
                return a + b;
            }
        """)
    ]

    @Override
    SourceFile getAlternateMainSource() {
        return sourceFile("objcpp", "main.mm", """
            // Simple hello world app
            #define __STDC_LIMIT_MACROS
            #include <stdint.h>
            #import <Foundation/Foundation.h>
            #import <iostream>
            #import "hello.h"

            int main (int argc, const char * argv[])
            {
                std::cout << "${HELLO_WORLD} ${HELLO_WORLD}" << std::endl;
                return 0;
            }
        """);
    }

    String alternateOutput = "${HELLO_WORLD} ${HELLO_WORLD}\n"


    @Override
    List<SourceFile> getAlternateLibrarySources() {
        return [
            sourceFile("objcpp", "hello.mm", """
            #define __STDC_LIMIT_MACROS
            #include <stdint.h>
            #include <iostream>
            #include "hello.h"

            void DLL_FUNC sayHello() {
                std::cout << "${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}" << std::endl;
            }
        """),
        sourceFile("objcpp", "sum.mm", """
            #include "hello.h"
            int DLL_FUNC sum(int a, int b) {
                return a + b;
            }
        """)]
    }

    String alternateLibraryOutput = "${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}\n12"

    public String getExtraConfiguration() {
        def linkerArgs = OperatingSystem.current().isMacOsX() ? '"-framework", "Foundation"' : '"-lgnustep-base", "-lobjc"'
        return """
            binaries.all {
                objcppCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                linker.args $linkerArgs
            }
        """
    }
    @Override
    List<String> getPluginList() {
        ['objective-cpp']
    }
}
