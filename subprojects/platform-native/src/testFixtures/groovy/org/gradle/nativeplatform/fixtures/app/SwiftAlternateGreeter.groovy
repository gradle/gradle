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

class SwiftAlternateGreeter extends SourceFileElement implements GreeterElement {
    @Override
    SourceFile getSourceFile() {
        sourceFile("swift", "greeter.swift", """
            public class Greeter {
                public init() {}
                public func sayHello() {
                    print("[${HelloWorldApp.HELLO_WORLD} - ${HelloWorldApp.HELLO_WORLD_FRENCH}]")
                }
            }

            // Extra function to ensure library has different size
            public func anotherFunction() -> Int {
                return 1000;
            }
        """)
    }

    @Override
    String getExpectedOutput() {
        return "[${HelloWorldApp.HELLO_WORLD} - ${HelloWorldApp.HELLO_WORLD_FRENCH}]\n"
    }
}
