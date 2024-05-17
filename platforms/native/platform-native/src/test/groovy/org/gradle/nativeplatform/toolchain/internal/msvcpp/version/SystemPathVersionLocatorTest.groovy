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

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.VersionNumber
import org.junit.Rule
import spock.lang.Specification

class SystemPathVersionLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def os = Mock(OperatingSystem)
    def versionDeterminer = Mock(VisualStudioMetaDataProvider)
    def locator = new SystemPathVersionLocator(os, versionDeterminer)

    def "can locate a known visual studio install on the path"() {
        def vsDir = tmpDir.createDir("vs")
        def compiler = vsDir.file("cl.exe")

        when:
        List<VisualStudioInstallCandidate> metadata = locator.getVisualStudioInstalls()

        then:
        1 * os.findInPath("cl.exe") >> compiler
        1 * versionDeterminer.getVisualStudioMetadataFromCompiler(compiler) >> vsMetadata(vsDir, "15.0")

        and:
        metadata.size() == 1
        metadata[0].installDir == vsDir
        metadata[0].version == VersionNumber.parse("15.0")
        metadata[0].visualCppDir == new File(vsDir, "VC")
        metadata[0].visualCppVersion == VersionNumber.parse("15.0")
    }

    def "can locate an unknown visual studio install on the path"() {
        def vsDir = tmpDir.createDir("vs")
        def compiler = vsDir.file("cl.exe")

        when:
        List<VisualStudioInstallCandidate> metadata = locator.getVisualStudioInstalls()

        then:
        1 * os.findInPath("cl.exe") >> compiler
        1 * versionDeterminer.getVisualStudioMetadataFromCompiler(compiler) >> vsMetadata(vsDir, null)

        and:
        metadata.size() == 1
        metadata[0].installDir == vsDir
        metadata[0].version == VersionNumber.UNKNOWN
        metadata[0].visualCppDir == new File(vsDir, "VC")
        metadata[0].visualCppVersion == VersionNumber.UNKNOWN
    }

    def "returns an empty list when no compilers are found on the path"() {
        when:
        List<VisualStudioInstallCandidate> metadata = locator.getVisualStudioInstalls()

        then:
        1 * os.findInPath("cl.exe") >> null
        0 * versionDeterminer.getVisualStudioMetadataFromCompiler(_)

        and:
        metadata.isEmpty()
    }

    def "returns an empty list when compiler is found on the path but isn't a recognizable install"() {
        def vsDir = tmpDir.createDir("vs")
        def compiler = vsDir.file("cl.exe")

        when:
        List<VisualStudioInstallCandidate> metadata = locator.getVisualStudioInstalls()

        then:
        1 * os.findInPath("cl.exe") >> compiler
        1 * versionDeterminer.getVisualStudioMetadataFromCompiler(compiler) >> null

        and:
        metadata.isEmpty()
    }

    VisualStudioInstallCandidate vsMetadata(File dir, String version) {
        return new VisualStudioMetadataBuilder()
            .installDir(dir)
            .version(VersionNumber.parse(version))
            .visualCppDir(new File(dir, "VC"))
            .visualCppVersion(VersionNumber.parse(version))
            .build()
    }
}
