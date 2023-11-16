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

class SwiftMainWithCppDep extends SourceFileElement implements AppElement {
    final GreeterElement greeter
    final SwiftSum sum
    String[] imports = ['cppGreeter']

    SwiftMainWithCppDep(GreeterElement greeter) {
        this(greeter, new SwiftSum())
    }

    SwiftMainWithCppDep(GreeterElement greeter, String[] imports) {
        this(greeter, new SwiftSum())
        this.imports = imports
    }

    SwiftMainWithCppDep(GreeterElement greeter, SwiftSum sum) {
        this.greeter = greeter
        this.sum = sum
    }

    @Override
    SourceFile getSourceFile() {
        return sourceFile("swift", "main.swift", """
            ${imports.collect { "import " + it }.join("\n")}
            
            // Simple hello world app
            func main() -> Int {
              sayGreeting()
              print(sum(a: 5, b: 7), terminator: "")
              return 0
            }
    
            _ = main()
        """)
    }

    @Override
    String getExpectedOutput() {
        return greeter.expectedOutput + sum.sum(5, 7)
    }

    @Override
    List<SourceFile> getFiles() {
        return super.getFiles() + sum.getFiles()
    }
}
