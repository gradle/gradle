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
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetadataBuilder
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionLocator
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.concurrent.ContentionGate
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.VersionNumber
import org.junit.Rule

class DefaultVisualStudioLocatorConcurrencyTest extends ConcurrentSpec {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final VisualStudioVersionLocator commandLineLocator = Stub(VisualStudioVersionLocator)
    final VisualStudioVersionLocator windowsRegistryLocator = Stub(VisualStudioVersionLocator)
    final VisualStudioLocator visualStudioLocator = new DefaultVisualStudioLocator(commandLineLocator, windowsRegistryLocator, Stub(VisualStudioVersionLocator), Stub(VisualStudioMetaDataProvider), Stub(SystemInfo))

    def "discovers installs once when located concurrently"() {
        def dir = tmpDir.createDir("vs")
        dir.createDir("Common7")
        dir.createFile("VC/bin/cl.exe")
        dir.createDir("VC/lib")
        def install = new VisualStudioMetadataBuilder()
            .installDir(dir)
            .visualCppDir(new File(dir, "VC"))
            .visualCppVersion(VersionNumber.parse("12.0"))
            .version(VersionNumber.parse("12.0"))
            .build()
        def gate = new ContentionGate()
        def results = [].asSynchronized()

        given:
        commandLineLocator.getVisualStudioInstalls() >> { gate.pass { [install] } }
        windowsRegistryLocator.getVisualStudioInstalls() >> []

        when:
        async {
            start { results << visualStudioLocator.locateComponent(null) }
            start { results << visualStudioLocator.locateComponent(null) }
            gate.releaseWhenContended()
        }

        then:
        gate.entries == 1
        results*.component*.baseDir == [dir, dir]
    }
}
