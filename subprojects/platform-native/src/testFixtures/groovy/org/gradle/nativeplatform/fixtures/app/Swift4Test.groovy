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

class Swift4Test extends XCTestSourceFileElement {
    Swift4Test() {
        super("Swift4Test")
    }

    @Override
    List<XCTestCaseElement> getTestCases() {
        return [
            testCase("testOnlyOneEntryWithSpecificFirstAndLastName",
            """// Assume only one entry
                getNames().forEach({ name in
                    XCTAssertEqual(name.firstName, "Bart")
                    XCTAssertEqual(name.lastName, "den Hollander")
                })"""),
            testCase("testMultiLineStringContainsSpecificString",
            '''XCTAssertNotNil(getLongMessage().range(of: """
                                Multi-line strings also let you write "quote marks"
                                freely inside your strings, which is great!
                                """))'''),
            testCase("testCodeWasCompiledWithSwift4Compiler",
                """#if swift(>=5.0)
                        XCTFail("Compilation unit compiled with Swift 5+ instead of Swift 4.x");
                    #elseif swift(>=4.0)
                        // Do nothing
                    #else
                        XCTFail("Compilation unit compiled with Swift 3- instead of Swift 4.x");
                    #endif
                """)
        ]
    }
}
