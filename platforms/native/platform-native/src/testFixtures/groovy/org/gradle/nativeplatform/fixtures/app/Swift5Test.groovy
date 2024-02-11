/*
 * Copyright 2019 the original author or authors.
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

class Swift5Test extends XCTestSourceFileElement {
    Swift5Test() {
        super("Swift5Test")
    }

    @Override
    List<XCTestCaseElement> getTestCases() {
        return [
            testCase("testRawStrings",
            '''XCTAssertEqual(getRawString(), "Raw string are ones with \\"quotes\\", backslash (\\\\), but can do special string interpolation (42)"))'''),
            testCase("testCodeWasCompiledWithSwift5Compiler",
                """#if swift(>=6.0)
                        XCTFail("Compilation unit compiled with Swift 6+ instead of Swift 5.x");
                    #elseif swift(>=5.0)
                        // Do nothing
                    #else
                        XCTFail("Compilation unit compiled with Swift 4- instead of Swift 5.x");
                    #endif
                """)
        ]
    }
}
