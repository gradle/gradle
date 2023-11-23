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

package org.gradle.nativeplatform.toolchain.internal.msvcpp.version

import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class VswhereSpec extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    File vswhere
    def localRoot = tmpDir.createDir("root")
    def programFiles = localRoot.createDir("Program Files")
    def programFilesX86 = localRoot.createDir("Program Files (X86)")
    def os = Mock(OperatingSystem)
    def windowsRegistry = Mock(WindowsRegistry)

    void vswhereInPath() {
        x64Registry()
        vswhere = localRoot.createFile("vswhere.exe")
    }

    void vswhereInProgramFiles() {
        x86Registry()
        vswhere = programFiles.createDir("Microsoft Visual Studio/Installer").createFile("vswhere.exe")
    }

    void vswhereInProgramFilesX86() {
        x64Registry()
        vswhere = programFilesX86.createDir("Microsoft Visual Studio/Installer").createFile("vswhere.exe")
    }

    void vswhereNotFound() {
        x64Registry()
    }

    void vswhereNotFoundX86Registry() {
        x86Registry()
    }

    void x64Registry() {
        _ * windowsRegistry.getStringValue(_, _, "ProgramFilesDir") >> programFiles.absolutePath
        _ * windowsRegistry.getStringValue(_, _, "ProgramFilesDir (x86)") >> programFilesX86.absolutePath
    }

    void x86Registry() {
        _ * windowsRegistry.getStringValue(_, _, "ProgramFilesDir") >> programFiles.absolutePath
        _ * windowsRegistry.getStringValue(_, _, "ProgramFilesDir (x86)") >> { throw new MissingRegistryEntryException("not found") }
    }
}
