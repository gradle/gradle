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

import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.internal.TextUtil
import org.gradle.util.internal.VersionNumber

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioInstallCandidate.Compatibility.LEGACY
import static org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER

class CommandLineToolVersionLocatorTest extends VswhereSpec {
    def visualCppMetadataProvider = Mock(VisualCppMetadataProvider)
    def execActionFactory = Mock(ExecActionFactory)
    def execAction = Mock(ExecAction)
    def vswhereLocator = Mock(VswhereVersionLocator)
    def locator = new CommandLineToolVersionLocator(execActionFactory, visualCppMetadataProvider, vswhereLocator)

    def setup() {
        _ * vswhereLocator.getVswhereInstall() >> { vswhere }
    }

    def "finds all versions of visual studio using vswhere"() {
        given:
        validInstallations()
        vswhereInProgramFiles()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata.size() == 3
        versionMetadata[0].version == VersionNumber.parse("15.3.26730.16")
        versionMetadata[0].installDir == new File(localRoot, "Program Files/Microsoft Visual Studio/2017/Community")
        versionMetadata[0].visualCppDir == new File(versionMetadata[0].installDir, "VC/Tools/MSVC/1.2.3.4")
        versionMetadata[0].visualCppVersion == VersionNumber.parse("1.2.3.4")
        versionMetadata[0].compatibility == VS2017_OR_LATER

        versionMetadata[1].version == VersionNumber.parse("14.0")
        versionMetadata[1].installDir == new File(localRoot, "Program Files/Microsoft Visual Studio 14.0/")
        versionMetadata[1].visualCppDir == new File(versionMetadata[1].installDir, "VC")
        versionMetadata[1].visualCppVersion == VersionNumber.parse("14.0")
        versionMetadata[1].compatibility == LEGACY

        versionMetadata[2].version == VersionNumber.parse("12.0")
        versionMetadata[2].installDir == new File(localRoot, "Program Files/Microsoft Visual Studio 12.0/")
        versionMetadata[2].visualCppDir == new File(versionMetadata[2].installDir, "VC")
        versionMetadata[2].visualCppVersion == VersionNumber.parse("12.0")
        versionMetadata[2].compatibility == LEGACY
    }

    def "returns empty list when vswhere returns no results"() {
        given:
        validInstallations()
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
        _ * vswhereLocator.getVswhereInstall() >> { new DefaultVswhereVersionLocator(windowsRegistry, os).getVswhereInstall() }
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
        validInstallations()
        vswhereInProgramFiles()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata.size() == 3

        when:
        locator.getVisualStudioInstalls()

        then:
        0 * vswhereLocator.getVswhereInstall()
        0 * execAction.execute()
    }

    def "ignores partial legacy installs when registry values do not exist"() {
        given:
        invalidLegacyInstallations()
        vswhereInProgramFiles()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata.size() == 1
        versionMetadata[0].version == VersionNumber.parse("15.3.26730.16")
    }

    def "ignores partial VS2017 installs when VisualCpp metadata file does not exist"() {
        given:
        invalidVS2017Installations()
        vswhereInProgramFiles()
        vswhereResults(vsWhereManyVersions(localRoot))

        when:
        def versionMetadata = locator.getVisualStudioInstalls()

        then:
        versionMetadata.size() == 2
        versionMetadata[0].version == VersionNumber.parse("14.0")
        versionMetadata[1].version == VersionNumber.parse("12.0")
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

    VisualCppInstallCandidate visualCppMetadata(File visualCppDir, String version) {
        return new VisualCppInstallCandidate() {
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

    void validInstallations() {
        _ * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> { args -> visualCppMetadata(new File(args[0], "VC/Tools/MSVC/1.2.3.4"), "1.2.3.4") }
        _ * visualCppMetadataProvider.getVisualCppFromRegistry(_) >> { args -> visualCppMetadata(localRoot.createDir("Program Files/Microsoft Visual Studio ${args[0]}/VC"), args[0]) }
    }

    void invalidLegacyInstallations() {
        _ * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> { args -> visualCppMetadata(new File(args[0], "VC/Tools/MSVC/1.2.3.4"), "1.2.3.4") }
        _ * visualCppMetadataProvider.getVisualCppFromRegistry(_) >> null
    }

    void invalidVS2017Installations() {
        _ * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> null
        _ * visualCppMetadataProvider.getVisualCppFromRegistry(_) >> { args -> visualCppMetadata(localRoot.createDir("Program Files/Microsoft Visual Studio ${args[0]}/VC"), args[0]) }
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
