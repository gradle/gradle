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

import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TextUtil
import org.gradle.util.VersionNumber
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetadata.Compatibility.*


class CommandLineToolVersionLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    File vswhere
    def localRoot = tmpDir.createDir("root")
    def programFiles = localRoot.createDir("Program Files")
    def programFilesX86 = localRoot.createDir("Program Files (X86)")
    def os = Mock(OperatingSystem)
    def windowsRegistry = Mock(WindowsRegistry)
    def visualCppMetadataProvider = Mock(VisualCppMetadataProvider)
    def execActionFactory = Mock(ExecActionFactory)
    def execAction = Mock(ExecAction)
    def locator = new CommandLineToolVersionLocator(execActionFactory, windowsRegistry, os, visualCppMetadataProvider)

    def setup() {
        _ * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> { args -> visualCppMetadata(new File(args[0], "VC/Tools/MSVC/1.2.3.4"), "1.2.3.4") }

        _ * visualCppMetadataProvider.getVisualCppFromRegistry(_) >> { args -> visualCppMetadata(localRoot.createDir("Program Files/Microsoft Visual Studio ${args[0]}/VC"), args[0]) }
    }

    def "finds vswhere executable in Program Files"() {
        given:
        vswhereInProgramFiles()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        locator.getVisualStudioInstalls()

        then:
        1 * execAction.executable(vswhere.absolutePath)
    }

    def "finds vswhere executable in Program Files (X86)"() {
        given:
        vswhereInProgramFilesX86()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        locator.getVisualStudioInstalls()

        then:
        1 * execAction.executable(vswhere.absolutePath)
    }

    def "finds vswhere executable in system path"() {
        given:
        vswhereInPath()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        locator.getVisualStudioInstalls()

        then:
        1 * execAction.executable(vswhere.absolutePath)
    }

    def "finds vswhere executable in system path if vswhere executable in Program Files is not a file"() {
        def programFilesVswhere = programFiles.createDir("Microsoft Visual Studio/Installer").createDir("vswhere.exe")

        given:
        vswhereInPath()
        assert vswhere.absolutePath != programFilesVswhere.absolutePath
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        locator.getVisualStudioInstalls()

        then:
        1 * execAction.executable(vswhere.absolutePath)
    }

    def "prefers vswhere executable from Program Files if also available in system path"() {
        def pathVswhere = localRoot.createFile("vswhere.exe")

        given:
        vswhereInProgramFiles()
        _ * os.findInPath("vswhere.exe") >> pathVswhere
        assert pathVswhere.absolutePath != vswhere.absolutePath
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        locator.getVisualStudioInstalls()

        then:
        1 * execAction.executable(vswhere.absolutePath)
    }

    def "prefers vswhere executable from Program Files (X86) if also available in system path"() {
        def pathVswhere = localRoot.createFile("vswhere.exe")

        given:
        vswhereInProgramFilesX86()
        _ * os.findInPath("vswhere.exe") >> pathVswhere
        assert pathVswhere.absolutePath != vswhere.absolutePath
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        locator.getVisualStudioInstalls()

        then:
        1 * execAction.executable(vswhere.absolutePath)
    }

    def "finds all versions of visual studio using vswhere"() {
        given:
        vswhereInProgramFiles()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata.size() == 3
        versionMetadata[0].version == VersionNumber.parse("15.3.26730.16")
        versionMetadata[0].installDir == new File("${TextUtil.escapeString(localRoot.absolutePath)}/Program Files/Microsoft Visual Studio/2017/Community")
        versionMetadata[0].visualCppDir == new File(versionMetadata[0].installDir, "VC/Tools/MSVC/1.2.3.4")
        versionMetadata[0].visualCppVersion == VersionNumber.parse("1.2.3.4")
        versionMetadata[0].compatibility == VS2017_OR_LATER

        versionMetadata[1].version == VersionNumber.parse("14.0")
        versionMetadata[1].installDir == new File("${TextUtil.escapeString(localRoot.absolutePath)}/Program Files/Microsoft Visual Studio 14.0/")
        versionMetadata[1].visualCppDir == new File(versionMetadata[1].installDir, "VC")
        versionMetadata[1].visualCppVersion == VersionNumber.parse("14.0")
        versionMetadata[1].compatibility == LEGACY

        versionMetadata[2].version == VersionNumber.parse("12.0")
        versionMetadata[2].installDir == new File("${TextUtil.escapeString(localRoot.absolutePath)}/Program Files/Microsoft Visual Studio 12.0/")
        versionMetadata[2].visualCppDir == new File(versionMetadata[2].installDir, "VC")
        versionMetadata[2].visualCppVersion == VersionNumber.parse("12.0")
        versionMetadata[2].compatibility == LEGACY
    }

    def "returns empty list when vswhere returns no results"() {
        given:
        vswhereInProgramFiles()
        vswhereResults(VSWHERE_NO_VERSIONS)

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata != null
        versionMetadata.size() == 0
    }

    def "returns empty list when vswhere cannot be found"() {
        given:
        vswhereNotFound()

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata != null
        versionMetadata.size() == 0
    }

    def "returns empty list if vswhere executable is not a file"() {
        given:
        x64Registry()
        vswhere = programFiles.createDir("Microsoft Visual Studio/Installer").createDir("vswhere.exe")

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata != null
        versionMetadata.size() == 0
    }

    def "returns empty list if Program Files (X86) cannot be found in registry"() {
        given:
        vswhereNotFoundX86Registry()

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata != null
        versionMetadata.size() == 0
    }

    def "caches versions after first invocation"() {
        given:
        vswhereInProgramFiles()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata.size() == 3

        when:
        locator.getVisualStudioInstalls()

        then:
        0 * windowsRegistry.getStringValue(_, _, _)
        0 * os.findInPath(_)
        0 * execAction.execute()
    }

    void vswhereResults(String jsonResult) {
        OutputStream outputStream

        1 * execActionFactory.newExecAction() >> execAction
        1 * execAction.setStandardOutput(_ as OutputStream) >> { args ->
            outputStream = args[0]
            return null
        }
        1 * execAction.execute() >> {
            outputStream.write(jsonResult.bytes)
            return Stub(ExecResult)
        }
    }

    void vswhereInPath() {
        x64Registry()
        vswhere = localRoot.createFile("vswhere.exe")
        1 * os.findInPath(_) >> vswhere
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
        1 * os.findInPath(_) >> null
    }

    void vswhereNotFoundX86Registry() {
        x86Registry()
        1 * os.findInPath(_) >> null
    }

    void x64Registry() {
        _ * windowsRegistry.getStringValue(_, _, "ProgramFilesDir") >> programFiles.absolutePath
        _ * windowsRegistry.getStringValue(_, _, "ProgramFilesDir (x86)") >> programFilesX86.absolutePath
    }

    void x86Registry() {
        _ * windowsRegistry.getStringValue(_, _, "ProgramFilesDir") >> programFiles.absolutePath
        _ * windowsRegistry.getStringValue(_, _, "ProgramFilesDir (x86)") >> { throw new MissingRegistryEntryException("not found") }
    }

    VisualCppMetadata visualCppMetadata(File visualCppDir, String version) {
        return new VisualCppMetadata() {
            @Override
            File getVisualCppDir() {
                return visualCppDir
            }

            @Override
            VersionNumber getVersion() {
                return VersionNumber.parse(version)
            }
        }
    }

    private String vsWhereManyVersions(File localRoot) {
        return """
            [
              {
                "instanceId": "daee671f",
                "installDate": "2017-10-05T20:23:05Z",
                "installationName": "VisualStudio/15.3.5+26730.16",
                "installationPath": "${TextUtil.escapeString(localRoot.absolutePath)}/Program Files/Microsoft Visual Studio/2017/Community",
                "installationVersion": "15.3.26730.16",
                "isPrerelease": false,
                "displayName": "Visual Studio Community 2017",
                "description": "Free, fully-featured IDE for students, open-source and individual developers",
                "enginePath": "C:\\\\Program Files\\\\Microsoft Visual Studio\\\\Installer\\\\resources\\\\app\\\\ServiceHub\\\\Services\\\\Microsoft.VisualStudio.Setup.Service",
                "channelId": "VisualStudio.15.Release",
                "channelPath": "C:\\\\Users\\\\IEUser\\\\AppData\\\\Local\\\\Microsoft\\\\VisualStudio\\\\Packages\\\\_Channels\\\\4CB340F5\\\\catalog.json",
                "channelUri": "https://aka.ms/vs/15/release/channel",
                "releaseNotes": "https://go.microsoft.com/fwlink/?LinkId=660469#15.3.26730.16",
                "thirdPartyNotices": "https://go.microsoft.com/fwlink/?LinkId=660485"
              },
              {
                "instanceId": "VisualStudio.14.0",
                "installationPath": "${TextUtil.escapeString(localRoot.absolutePath)}/Program Files/Microsoft Visual Studio 14.0/",
                "installationVersion": "14.0"
              },
              {
                "instanceId": "VisualStudio.12.0",
                "installationPath": "${TextUtil.escapeString(localRoot.absolutePath)}/Program Files/Microsoft Visual Studio 12.0/",
                "installationVersion": "12.0"
              }
            ]
        """
    }

    private static final String VSWHERE_NO_VERSIONS = """
        []
    """
}
