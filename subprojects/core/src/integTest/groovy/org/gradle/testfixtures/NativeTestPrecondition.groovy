/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testfixtures

import static org.gradle.util.TestPrecondition.*

final class NativeTestPrecondition {
    private NativeTestPrecondition() {}

    static boolean isMacOsXOrLinuxOrWindows() {
        MAC_OS_X.fulfilled || LINUX.fulfilled || WINDOWS.fulfilled
    }

    static boolean isNotMacOsXOrLinuxOrWindows() {
        !MAC_OS_X.fulfilled && !LINUX.fulfilled && !WINDOWS.fulfilled
    }

    static boolean isMountedNoexec(String dir) {
        if (NOT_LINUX) {
            return false
        }

        def out = new StringBuffer()
        'mount'.execute().waitForProcessOutput(out, System.err)
        out.readLines().find { it.startsWith("tmpfs on $dir type tmpfs") && it.contains('noexec') } != null
    }
}
