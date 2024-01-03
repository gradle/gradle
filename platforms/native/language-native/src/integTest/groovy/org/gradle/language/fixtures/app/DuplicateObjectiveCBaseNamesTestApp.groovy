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

class DuplicateObjectiveCBaseNamesTestApp extends TestNativeComponent{

    def plugins = ["objective-c"]
    @Override
    List<SourceFile> getSourceFiles() {
        [sourceFile("objc", "main.m", """
            #import <Foundation/Foundation.h>
            #import "foo.h"

            int main (int argc, const char * argv[])
            {
                sayFoo1();
                sayFoo2();
                return 0;
            }
        """),
          fooSource(1),
          fooSource(2)]
    }

    public String getExtraConfiguration() {
        return """
            binaries {
                all {
                    if (targetPlatform.operatingSystem.macOsX) {
                        linker.args "-framework", "Foundation"
                    } else {
                        objcCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                        linker.args "-lgnustep-base", "-lobjc"
                    }
                }
            }
        """
    }

    @Override
    List<SourceFile> getHeaderFiles() {
        [sourceFile("headers", "foo.h", """
            void sayFoo1();
            void sayFoo2();
           """)
        ]
    }

    SourceFile fooSource(int index) {
        sourceFile("objc/foo$index", "foo.m", """
            #import <Foundation/Foundation.h>
            #import "foo.h"

            void sayFoo$index()
            {
                NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
                NSData *strData = [@"foo$index" dataUsingEncoding: NSASCIIStringEncoding];
                [stdout writeData: strData];
            }
        """)
    }
}
