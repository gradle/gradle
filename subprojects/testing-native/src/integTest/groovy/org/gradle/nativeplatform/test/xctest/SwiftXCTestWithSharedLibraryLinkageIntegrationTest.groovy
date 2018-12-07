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

import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTest
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement

class SwiftXCTestWithSharedLibraryLinkageIntegrationTest extends AbstractSwiftXCTestWithComponentIntegrationTest {
    @Override
    protected String[] getTasksToAssembleComponentUnderTest() {
        return [":linkDebug"]
    }

    @Override
    protected String[] getTasksToCompileComponentUnderTest() {
        return [":compileDebugSwift"]
    }

    @Override
    protected XCTestSourceElement getPassingTestFixture() {
        return new SwiftLibWithXCTest()
    }

    @Override
    protected XCTestSourceElement getPassingTestFixtureUsingPublicAndInternalFeatures() {
        return new SwiftLibWithXCTest()
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'xctest'
            apply plugin: 'swift-library'
        """
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }
}
