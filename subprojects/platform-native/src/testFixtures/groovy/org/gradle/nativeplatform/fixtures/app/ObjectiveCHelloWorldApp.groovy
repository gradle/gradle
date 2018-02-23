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

class ObjectiveCHelloWorldApp extends IncrementalHelloWorldApp {

    @Override
    SourceFile getMainSource() {
        return sourceFile("objc", "main.m", """
            // Simple hello world app
            #include "hello.h"

            int main(int argc, const char * argv[])
            {
                Greeter* greeter = [Greeter new];
                [greeter sayHello];
                [greeter release];
                printf("%d", sum(7, 5));
                return 0;
            }
        """)
    }

    @Override
    SourceFile getAlternateMainSource() {
        return sourceFile("objc", "main.m", """
            #import "hello.h"

            int main (int argc, const char * argv[])
            {
                Greeter* greeter = [Greeter new];
                [greeter sayHello];
                [greeter release];
                return 0;
            }
        """)
    }

    String alternateOutput = "$HELLO_WORLD\n"

    @Override
    SourceFile getLibraryHeader() {
        return sourceFile("headers", "hello.h", """
            #ifndef HELLO_H
            #define HELLO_H
            #import <Foundation/Foundation.h>

            @interface Greeter : NSObject
                - (void)sayHello;
            @end

            int sum(int a, int b);

            #ifdef FRENCH
            #pragma message("<==== compiling bonjour.h ====>")
            #else
            #pragma message("<==== compiling hello.h ====>")
            #endif

            #endif
        """)
    }

    @Override
    SourceFile getCommonHeader() {
        sourceFile("headers", "common.h", """
            #ifndef COMMON_H
            #define COMMON_H
            #include "hello.h"
            #include <stdio.h>
            #endif
        """)
    }

    @Override
    List<SourceFile> getLibrarySources() {
        return [
                sourceFile("objc", "hello.m", """
            #include "common.h"

            @implementation Greeter
            - (void) sayHello {
                NSString *helloWorld = @"${HELLO_WORLD}";
                #ifdef FRENCH
                helloWorld = @"${HELLO_WORLD_FRENCH}";
                #endif
                fprintf(stdout, "%s\\n", [helloWorld UTF8String]);
            }
            @end
        """),
                sourceFile("objc", "sum.m", """
            #include "common.h"

            int sum (int a, int b)
            {
                return a + b;
            }
        """)]
    }

    @Override
    List<SourceFile> getAlternateLibrarySources() {
        return [
                sourceFile("objc", "hello.m", """
            #include "common.h"

            @implementation Greeter
            - (void) sayHello {
                NSString *helloWorld = @"${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}";
                fprintf(stdout, "%s\\n", [helloWorld UTF8String]);
            }
            @end

            // Extra function to ensure library has different size
            int anotherFunction() {
                return 1000;
            }
        """),
                sourceFile("objc", "sum.m", """
            #include "common.h"

            int sum(int a, int b)
            {
                return a + b;
            }
        """)]
    }

    String alternateLibraryOutput = "${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}\n12"

    String getExtraConfiguration(String binaryName = null) {
        return """
            model {
                binaries {
                    ${binaryName ? binaryName : "all"} {
                        if (targetPlatform.operatingSystem.macOsX) {
                            linker.args "-framework", "Foundation"
                        } else {
                            objcCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                            linker.args "-lgnustep-base", "-lobjc"
                        }
                    }
                }
            }
        """
    }

    @Override
    List<String> getPluginList() {
        ['objective-c']
    }

    SourceFile getBrokenFile() {
        return sourceFile("objc", "broken.m", """'broken""")
    }

    @Override
    String getSourceSetType() {
        return "ObjectiveCSourceSet"
    }
}
