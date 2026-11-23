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

import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.concurrent.ContentionGate
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

class LegacyWindowsSdkLocatorConcurrencyTest extends ConcurrentSpec {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final WindowsRegistry windowsRegistry = Stub(WindowsRegistry)
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
        getExecutableName(_ as String) >> { String exeName -> exeName }
    }
    final WindowsSdkLocator windowsSdkLocator = new LegacyWindowsSdkLocator(operatingSystem, windowsRegistry)

    def "discovers SDKs once when located concurrently"() {
        def dir = tmpDir.createDir("sdk")
        dir.createFile("bin/rc.exe")
        dir.createFile("lib/kernel32.lib")
        def gate = new ContentionGate()
        def results = [].asSynchronized()

        given:
        operatingSystem.findInPath(_) >> { gate.pass { null } }
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows/) >> ["v1"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> dir.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.0"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "sdk 1"

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
