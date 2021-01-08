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

class Swift4 extends SwiftSourceElement {
    Swift4(String projectName) {
        super(projectName)
    }

    @Override
    List<SourceFile> getFiles() {
        return [sourceFile("swift", "swift4-code.swift", '''
            public typealias Name = (firstName: String, lastName: String)

            public func getNames() -> [Name] {
                return [("Bart", "den Hollander")]
            }

            public func getLastNameOfFirstEntry(names: [Name]) -> String {
                var result: String = ""
                names.forEach({ name in
                    result = name.lastName  // "den Hollander"
                })
                return result
            }

            public func getLongMessage() -> String {
                return """
                        When you write a string that spans multiple
                        lines make sure you start its content on a
                        line all of its own, and end it with three
                        quotes also on a line of their own.
                        Multi-line strings also let you write "quote marks"
                        freely inside your strings, which is great!
                        """
            }
        ''')]
    }
}
