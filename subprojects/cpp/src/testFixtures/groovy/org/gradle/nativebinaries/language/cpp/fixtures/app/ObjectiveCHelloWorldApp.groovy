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

class ObjectiveCHelloWorldApp extends IncrementalHelloWorldApp {

    @Override
    SourceFile getMainSource() {
        return sourceFile("objc", "main.m", """
            // Simple hello world app
            #import <Foundation/Foundation.h>
            #import "hello.h"

            int main (int argc, const char * argv[])
            {
                sayHello();
                printf("%d", sum(7, 5));
                return 0;
            }
        """);
    }

    @Override
    SourceFile getAlternateMainSource() {
        return sourceFile("objc", "main.m", """
            #import <Foundation/Foundation.h>
            #import "hello.h"

            int main (int argc, const char * argv[])
            {
                sayHello();
                return 0;
            }
        """);
    }

    String alternateOutput = "$HELLO_WORLD\n"


    @Override
    SourceFile getLibraryHeader() {
        return sourceFile("headers", "hello.h", """
            int main (int argc, const char * argv[]);
            int sum(int a, int b);
            void sayHello();
        """);
    }

    @Override
    List<SourceFile> getLibrarySources() {
        return [
                sourceFile("objc", "hello.m", """
            #import <Foundation/Foundation.h>
            #import "hello.h"

            void sayHello()
            {
                #ifdef FRENCH
                NSString *helloWorld = @"${HELLO_WORLD_FRENCH}\\n";
                #else
                NSString *helloWorld = @"${HELLO_WORLD}\\n";
                #endif
                NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
                NSData *strData = [helloWorld dataUsingEncoding: NSASCIIStringEncoding];
                [stdout writeData: strData];
            }
        """),
                sourceFile("objc", "sum.m", """
            #import "hello.h"

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
            #import <Foundation/Foundation.h>
            #import "hello.h"

            void sayHello()
            {
                NSString *helloWorld = @"${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}\\n";
                NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
                NSData *strData = [helloWorld dataUsingEncoding: NSASCIIStringEncoding];
                [stdout writeData: strData];
            }

            // Extra function to ensure library has different size
            int anotherFunction() {
                return 1000;
            }
        """),
                sourceFile("objc", "sum.m", """
            #import "hello.h"

            int sum (int a, int b)
            {
                return a + b;
            }
        """)]
    }

    String alternateLibraryOutput = "${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}\n12"

    public String getExtraConfiguration() {
        def linkerArgs = OperatingSystem.current().isMacOsX() ? '"-framework", "Foundation"' : '"-lgnustep-base", "-lobjc"'
        return """
            binaries.all {
                objcCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                linker.args $linkerArgs
            }
        """
    }

    @Override
    List<String> getPluginList() {
        ['objective-c']
    }
}
