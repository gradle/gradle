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

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioInstallCandidate.Compatibility.LEGACY

class WindowsRegistryVersionLocatorTest extends Specification {
    public static final String SOFTWARE_KEY = "SOFTWARE\\Microsoft\\VisualStudio\\SxS\\VC7"
    public static final String SOFTWARE_WOW6432_KEY = "SOFTWARE\\Wow6432Node\\Microsoft\\VisualStudio\\SxS\\VC7"
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def windowsRegistry = Mock(WindowsRegistry)
    def locator = new WindowsRegistryVersionLocator(windowsRegistry)

    def "can locate installed versions in windows registry (#architecture)"() {
        given:
        def dir1 = tmpDir.createDir("Visual Studio 14.0")
        def dir2 = tmpDir.createDir("Visual Studio 12.0")
        def dir3 = tmpDir.createDir("Visual Studio 11.0")

        when:
        List<VisualStudioInstallCandidate> metadata = locator.getVisualStudioInstalls()

        then:
        switch(foundIn) {
            case SOFTWARE_KEY:
                1 * windowsRegistry.getValueNames(_, SOFTWARE_KEY) >> ["14.0", "12.0", "11.0" ]
                1 * windowsRegistry.getValueNames(_, SOFTWARE_WOW6432_KEY) >> { throw new MissingRegistryEntryException("not found") }
                break;
            case SOFTWARE_WOW6432_KEY:
                1 * windowsRegistry.getValueNames(_, SOFTWARE_KEY) >> { throw new MissingRegistryEntryException("not found") }
                1 * windowsRegistry.getValueNames(_, SOFTWARE_WOW6432_KEY) >> ["14.0", "12.0", "11.0" ]
                break;
        }
        1 * windowsRegistry.getStringValue(_, foundIn, "14.0") >> dir1.createDir("VC").absolutePath
        1 * windowsRegistry.getStringValue(_, foundIn, "12.0") >> dir2.createDir("VC").absolutePath
        1 * windowsRegistry.getStringValue(_, foundIn, "11.0") >> dir3.createDir("VC").absolutePath

        and:
        metadata.size() == 3
        metadata[0].installDir == dir1
        metadata[0].visualCppDir == new File(dir1, "VC")
        metadata[0].visualCppVersion == VersionNumber.parse("14.0")
        metadata[0].version == VersionNumber.parse("14.0")
        metadata[0].compatibility == LEGACY

        metadata[1].installDir == dir2
        metadata[1].visualCppDir == new File(dir2, "VC")
        metadata[1].visualCppVersion == VersionNumber.parse("12.0")
        metadata[1].version == VersionNumber.parse("12.0")
        metadata[1].compatibility == LEGACY

        metadata[2].installDir == dir3
        metadata[2].visualCppDir == new File(dir3, "VC")
        metadata[2].visualCppVersion == VersionNumber.parse("11.0")
        metadata[2].version == VersionNumber.parse("11.0")
        metadata[2].compatibility == LEGACY

        where:
        foundIn              | architecture
        SOFTWARE_KEY         | "x86"
        SOFTWARE_WOW6432_KEY | "x64"
    }

    def "returns empty list when no installation found in registry"() {
        when:
        List<VisualStudioInstallCandidate> metadata = locator.getVisualStudioInstalls()

        then:
        1 * windowsRegistry.getValueNames(_, SOFTWARE_KEY) >> { throw new MissingRegistryEntryException("not found") }
        1 * windowsRegistry.getValueNames(_, SOFTWARE_WOW6432_KEY) >> { throw new MissingRegistryEntryException("not found") }

        and:
        metadata.size() == 0
    }

    def "caches metadata after first call"() {
        given:
        def dir1 = tmpDir.createDir("Visual Studio 14.0")
        def dir2 = tmpDir.createDir("Visual Studio 12.0")
        def dir3 = tmpDir.createDir("Visual Studio 11.0")

        when:
        List<VisualStudioInstallCandidate> metadata = locator.getVisualStudioInstalls()

        then:
        1 * windowsRegistry.getValueNames(_, SOFTWARE_KEY) >> ["14.0", "12.0", "11.0" ]
        1 * windowsRegistry.getValueNames(_, SOFTWARE_WOW6432_KEY) >> { throw new MissingRegistryEntryException("not found") }
        1 * windowsRegistry.getStringValue(_, _, "14.0") >> dir1.createDir("VC").absolutePath
        1 * windowsRegistry.getStringValue(_, _, "12.0") >> dir2.createDir("VC").absolutePath
        1 * windowsRegistry.getStringValue(_, _, "11.0") >> dir3.createDir("VC").absolutePath

        and:
        metadata.size() == 3

        when:
        metadata = locator.getVisualStudioInstalls()

        then:
        0 * windowsRegistry._

        and:
        metadata.size() == 3
    }

    def "ignores bogus versions in registry"() {
        given:
        def dir1 = tmpDir.createDir("Visual Studio 14.0")
        def dir2 = tmpDir.createDir("Visual Studio 12.0")
        def dir3 = tmpDir.createDir("Visual Studio 11.0")

        when:
        List<VisualStudioInstallCandidate> metadata = locator.getVisualStudioInstalls()

        then:
        1 * windowsRegistry.getValueNames(_, SOFTWARE_KEY) >> ["", "14.0", "12.0", "11.0", "ignore-me" ]
        1 * windowsRegistry.getValueNames(_, SOFTWARE_WOW6432_KEY) >> { throw new MissingRegistryEntryException("not found") }
        1 * windowsRegistry.getStringValue(_, _, "14.0") >> dir1.createDir("VC").absolutePath
        1 * windowsRegistry.getStringValue(_, _, "12.0") >> dir2.createDir("VC").absolutePath
        1 * windowsRegistry.getStringValue(_, _, "11.0") >> dir3.createDir("VC").absolutePath

        and:
        metadata.size() == 3
        metadata.collect { it.version.toString() } == [ "14.0.0", "12.0.0", "11.0.0" ]
    }
}
