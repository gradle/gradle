/*
 * Copyright 2024 the original author or authors.
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
import org.junit.Assume

/**
 * This class probes for an XCTest installation in a location where it will be discovered by the Swift plugins.
 */
class XCTestInstallation {
    static boolean isInstalled() {
        if (OperatingSystem.current().isMacOsX()) {
            // XCTest is bundled with XCode, so the test cannot be run if XCode is not installed
            def result = ["xcrun", "--show-sdk-platform-path"].execute().waitFor()
            // If it fails, assume XCode is not installed
            return result == 0
        } else {
            return true
        }
    }

    /**
     * Verifies that XCTest is installed. Currently, this just checks whether XCode is installed.
     */
    static void assumeInstalled() {
        if (!installed) {
            println "XCode is not installed"
            Assume.assumeTrue("XCode should be installed", false)
        }
    }
}
