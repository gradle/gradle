/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.language.AbstractNativeUnitTestComponentDependenciesIntegrationTest
import org.gradle.language.swift.SwiftTaskNames
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftXCTestWithDepAndCustomXCTestSuite
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_5_OR_OLDER)
@Requires(UnitTestPreconditions.HasXCTest)
@DoesNotSupportNonAsciiPaths(reason = "swiftc does not support these paths")
class XCTestWithApplicationDependenciesIntegrationTest extends AbstractNativeUnitTestComponentDependenciesIntegrationTest implements SwiftTaskNames {
    @Override
    protected void makeTestSuiteAndComponentWithLibrary() {
        buildFile << """
            apply plugin: 'xctest'
            apply plugin: 'swift-application'
            project(':lib') {
                apply plugin: 'swift-library'
            }
"""
        file("src/main/swift/App.swift") << """
            import Lib

            class App {
                var util = Util()
            }
"""
        def testSource = new SwiftXCTestWithDepAndCustomXCTestSuite("app", "App", "XCTAssertNotNil(App().util)", [] as String[], ["Root"] as String[])
        testSource.writeToProject(testDirectory)

        file("lib/src/main/swift/Util.swift") << """
            public class Util {
                public init() { }
            }
"""
    }

    @Override
    protected String getProductionComponentDsl() {
        return "application"
    }

    @Override
    protected List<String> getRunTestTasks() {
        return [tasks.debug.compile, tasks.test.relocate, tasks.test.allToInstall, ":xcTest"]
    }

    @Override
    protected List<String> getLibDebugTasks() {
        return [tasks(":lib").debug.allToLink]
    }
}
