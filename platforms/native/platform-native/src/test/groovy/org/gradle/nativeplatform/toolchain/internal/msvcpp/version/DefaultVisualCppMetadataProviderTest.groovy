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

package org.gradle.nativeplatform.toolchain.internal.msvcpp.version

import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.VersionNumber
import org.junit.Rule
import spock.lang.Specification

class DefaultVisualCppMetadataProviderTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def windowsRegistry = Mock(WindowsRegistry)
    def metadataProvider = new DefaultVisualCppMetadataProvider(windowsRegistry)

    def "can derive metadata from registry"() {
        def visualCppDir = tmpDir.createDir("dir1")

        given:
        1 * windowsRegistry.getStringValue(_, _, "14.0") >> visualCppDir

        when:
        VisualCppInstallCandidate metadata = metadataProvider.getVisualCppFromRegistry("14.0")

        then:
        metadata.version == VersionNumber.parse("14.0")
        metadata.visualCppDir == visualCppDir
    }

    def "returns null when metadata cannot be derived from registry"() {
        given:
        2 * windowsRegistry.getStringValue(_, _, "14.0") >> { throw new MissingRegistryEntryException("not found") }

        expect:
        metadataProvider.getVisualCppFromRegistry("14.0") == null
    }

    def "can derive metadata from compiler metadata file"() {
        def installDir = tmpDir.createDir("dir1")

        given:
        installDir.createFile("VC/Auxiliary/Build/Microsoft.VCToolsVersion.default.txt").text = "1.2.3.4"

        when:
        VisualCppInstallCandidate metadata = metadataProvider.getVisualCppFromMetadataFile(installDir)

        then:
        metadata.version == VersionNumber.parse("1.2.3.4")
        metadata.visualCppDir == new File(installDir, "VC/Tools/MSVC/1.2.3.4")
    }

    def "returns null when metadata cannot be derived from compiler metadata file"() {
        def installDir = tmpDir.createDir("dir1")

        expect:
        metadataProvider.getVisualCppFromMetadataFile(installDir) == null
    }
}
