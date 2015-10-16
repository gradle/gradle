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

package org.gradle.language.fixtures.app

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.nativeplatform.fixtures.app.TestNativeComponent

class DuplicateObjectiveCppBaseNamesTestApp extends TestNativeComponent{
    def plugins = ["objective-cpp"]
    @Override
    List<SourceFile> getSourceFiles() {
        [sourceFile("objcpp", "main.mm", """
            #define __STDC_LIMIT_MACROS
            #include <stdint.h>
            #import <Foundation/Foundation.h>
            #import "foo.h"

            int main (int argc, const char * argv[])
            {
                sayFoo1();
                sayFoo2();
                return 0;
            }
        """),
            sourceFile("objcpp/foo1", "foo.mm", """
            #define __STDC_LIMIT_MACROS
            #include <stdint.h>
            #import <Foundation/Foundation.h>
            #import "foo.h"

            void sayFoo1()
            {
                NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
                NSData *strData = [@"foo1" dataUsingEncoding: NSASCIIStringEncoding];
                [stdout writeData: strData];
            }
        """),
                sourceFile("objcpp/foo2", "foo.mm", """
            #import <iostream>
            #import "foo.h"

            void sayFoo2()
            {
                std::cout << "foo2";
            }
        """)]
    }

    @Override
    List<SourceFile> getHeaderFiles() {
        [sourceFile("headers", "foo.h", """
            void sayFoo1();
            void sayFoo2();
           """)
        ]
    }

    public String getExtraConfiguration() {
        return """
            binaries {
                all {
                    if (targetPlatform.operatingSystem.macOsX) {
                        linker.args "-framework", "Foundation"
                    } else {
                        objcppCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                        linker.args "-lgnustep-base", "-lobjc"
                    }
                }
            }
        """
    }
}
