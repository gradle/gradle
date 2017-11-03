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

package org.gradle.nativeplatform.fixtures.xctest

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AvailableToolChains

class XCTestFinderFixture {
    private final AvailableToolChains.InstalledToolChain toolChain

    XCTestFinderFixture(AvailableToolChains.InstalledToolChain toolChain) {
        this.toolChain = toolChain
    }

    String buildscript() {
        if (OperatingSystem.current().linux) {
"""
    allprojects {
        pluginManager.withPlugin("xctest") {
            dependencies {
                swiftCompileTest files('${xcTestImportPath}')
                nativeLinkTest files('${xcTestLinkFile}')
                nativeRuntimeTest files('${xcTestRuntimeFile}')
            }
        }
    }
"""
        } else {
            ""
        }
    }

    private File getXcTestImportPath() {
        File result = find('lib/swift/linux/x86_64/XCTest.swiftmodule')
        return result.parentFile
    }

    private String getXcTestLinkFile() {
        File result = find('lib/swift/linux/libXCTest.so')
        return result.absolutePath
    }

    private String getXcTestRuntimeFile() {
        return xcTestLinkFile
    }

    private File find(String file) {
        def searchedPaths = []
        for (File pathEntry : toolChain.getPathEntries()) {
            File result = new File(pathEntry.parentFile, file)
            searchedPaths << result
            if (result.exists()) {
                return result
            }
        }

        throw new IllegalStateException("Could not find '$file'. Searched: " + searchedPaths)
    }
}
