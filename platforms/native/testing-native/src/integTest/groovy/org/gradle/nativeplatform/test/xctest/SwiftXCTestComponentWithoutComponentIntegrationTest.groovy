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

package org.gradle.nativeplatform.test.xctest

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.app.SwiftXCTest
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement

class SwiftXCTestComponentWithoutComponentIntegrationTest extends AbstractSwiftXCTestComponentIntegrationTest {
    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'xctest'
        """
    }

    @Override
    protected XCTestSourceElement getComponentUnderTest() {
        return new SwiftXCTest('project')
    }

    @Override
    void assertComponentUnderTestWasBuilt() {
        if (OperatingSystem.current().linux) {
            executable("build/exe/test/${componentUnderTest.moduleName}").assertExists()
            installation("build/install/test").assertInstalled()
        } else {
            machOBundle("build/exe/test/${componentUnderTest.moduleName}").assertExists()
            file("build/install/test/${componentUnderTest.moduleName}").assertIsFile()
            file("build/install/test/${componentUnderTest.moduleName}.xctest").assertIsDir()
        }
    }
}
