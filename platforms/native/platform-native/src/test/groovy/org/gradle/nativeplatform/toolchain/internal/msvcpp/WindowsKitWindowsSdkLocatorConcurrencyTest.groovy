/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.concurrent.ContentionGate
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.AbstractWindowsKitComponentLocator.PLATFORMS

class WindowsKitWindowsSdkLocatorConcurrencyTest extends ConcurrentSpec {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final WindowsRegistry windowsRegistry = Stub(WindowsRegistry)
    final SystemInfo systemInfo = Stub(SystemInfo) {
        getArchitecture() >> SystemInfo.Architecture.amd64
    }
    final WindowsComponentLocator<WindowsKitSdkInstall> windowsSdkLocator = new WindowsKitWindowsSdkLocator(windowsRegistry, systemInfo)

    def "discovers kits once when located concurrently"() {
        def version = "10.0.10150.0"
        def dir = tmpDir.createDir("sdk")
        PLATFORMS.each { dir.createFile("bin/${it}/rc.exe") }
        dir.createFile("Include/${version}/um/windows.h")
        PLATFORMS.each { dir.createFile("Lib/${version}/um/${it}/kernel32.lib") }
        def gate = new ContentionGate()
        def results = [].asSynchronized()

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> {
            gate.pass { dir.absolutePath }
        }

        when:
        async {
            start { results << windowsSdkLocator.locateComponent(null) }
            start { results << windowsSdkLocator.locateComponent(null) }
            gate.releaseWhenContended()
        }

        then:
        gate.entries == 1
        results*.component*.baseDir == [dir, dir]
    }
}
