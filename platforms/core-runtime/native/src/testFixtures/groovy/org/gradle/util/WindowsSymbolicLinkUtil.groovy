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

package org.gradle.util

class WindowsSymbolicLinkUtil {
    static void createWindowsSymbolicLink(File link, File target) {
        def extraOptions = target.isDirectory() ? ["/d"] : []
        assert ["cmd.exe", "/d", "/c", "mklink", *extraOptions, link, target].execute().waitFor() == 0
    }

    static void createWindowsJunction(File link, File target) {
        assert target.isDirectory(), "Windows junction only works on directory"
        assert ["cmd.exe", "/d", "/c", "mklink", "/j", link, target].execute().waitFor() == 0
    }

    static void createWindowsHardLinks(File link, File target) {
        assert target.isFile(), "Windows hard links only works on files"
        assertAdministrator()
        assert ["cmd.exe", "/d", "/c", "mklink", "/h", link, target].execute().waitFor() == 0
    }

    // See: https://support.microsoft.com/en-us/help/243330/well-known-security-identifiers-in-windows-operating-systems
    private static final String WELL_KNOWN_ADMINISTRATORS_GROUP_SID = "S-1-5-32-544"
    private static void assertAdministrator() {
        assert ["cmd.exe", "/d", "/c", "whoami", "/groups"].execute().text.contains(WELL_KNOWN_ADMINISTRATORS_GROUP_SID)
    }
}
