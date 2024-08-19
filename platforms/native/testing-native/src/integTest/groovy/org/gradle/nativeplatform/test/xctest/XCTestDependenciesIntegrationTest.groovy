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

import org.gradle.language.AbstractNativeDependenciesIntegrationTest
import org.gradle.language.swift.SwiftTaskNames
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
@DoesNotSupportNonAsciiPaths(reason = "swiftc does not support these paths")
class XCTestDependenciesIntegrationTest extends AbstractNativeDependenciesIntegrationTest implements SwiftTaskNames {
    def setup() {
        // Need XCTest available to run these tests
        XCTestInstallation.assumeInstalled()
    }

    @Override
    protected void makeComponentWithLibrary() {
        buildFile << """
            apply plugin: 'xctest'
            project(':lib') {
                apply plugin: 'swift-library'
            }
"""
        file("src/test/swift/Test.swift") << """
            import Lib
            import XCTest

            class Test {
                var util = Util()
            }
"""
        file("lib/src/main/swift/Util.swift") << """
            public class Util {
                public init() { }
            }
"""
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "xctest"
    }

    @Override
    protected String getAssembleDevBinaryTask() {
        return tasks.test.install
    }

    @Override
    protected List<String> getAssembleDevBinaryTasks() {
        return tasks.test.allToLink
    }

    @Override
    protected List<String> getLibDebugTasks() {
        return tasks(':lib').debug.allToLink
    }
}
