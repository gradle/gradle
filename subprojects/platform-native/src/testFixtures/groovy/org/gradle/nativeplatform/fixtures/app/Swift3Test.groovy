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

class Swift3Test extends XCTestSourceFileElement {
    Swift3Test() {
        super("Swift3Test")
    }

    @Override
    List<XCTestCaseElement> getTestCases() {
        return [
            testCase("testOnlyOneEntryWithSpecificFirstAndLastName",
                """// Assume only one entry
                getNames().forEach({ first, last in
                    XCTAssertEqual(first, "Bart")
                    XCTAssertEqual(last, "den Hollander")
                })"""),
            testCase("testMultiLineStringContainsSpecificString",
                '''XCTAssertNotNil(getLongMessage().range(of: "Multi-line strings also let you write \\"quote marks\\"\\nfreely inside your strings, which is great!"))'''),
            testCase("testCodeWasCompiledWithSwift3Compiler",
                """#if swift(>=4.0)
                        XCTFail("Compilation unit compiled with Swift 4+ instead of Swift 3.x");
                    #elseif swift(>=3.0)
                        // Do nothing
                    #else
                        XCTFail("Compilation unit compiled with Swift 2- instead of Swift 3.x");
                    #endif
                """)
        ]
    }
}
