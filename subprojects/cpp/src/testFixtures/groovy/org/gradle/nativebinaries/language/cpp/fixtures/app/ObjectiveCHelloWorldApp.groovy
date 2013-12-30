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

class ObjectiveCHelloWorldApp extends HelloWorldApp {
    @Override
    SourceFile getMainSource() {
        return sourceFile("objc", "main.m", """
            #import <cocoa/cocoa.h>
            #import "hello.h"

            int main (int argc, const char * argv[])
            {
                NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

                sayHello();
                printf("%d", sum(7, 5));

                [pool drain];
                return 0;
            }
        """);
    }

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
            #import <cocoa/cocoa.h>
            #import "hello.h"

            void sayHello()
            {
                #ifdef FRENCH
                printf("%s", [@"${HELLO_WORLD_FRENCH}\\n" UTF8String]);
                #else
                printf("%s", [@"${HELLO_WORLD}\\n" UTF8String]);
                #endif
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
}
