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

package org.gradle.ide.xcode.fixtures

import com.google.common.base.Splitter
import org.gradle.ide.fixtures.IdeCommandLineUtil
import org.gradle.ide.xcode.internal.DefaultXcodeProject
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.HostPlatform
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.junit.Assume.assumeTrue

@Requires(value = [
    UnitTestPreconditions.HasXCode,
    UnitTestPreconditions.NotMacOsM1
], reason = "M1 Macs need modern Xcode to compile aarch64 binaries")
class AbstractXcodeIntegrationSpec extends AbstractIntegrationSpec implements HostPlatform {
    AvailableToolChains.InstalledToolChain toolChain = null

    def setup() {
        buildFile << """
allprojects {
    apply plugin: 'xcode'
}
"""
        settingsFile << """
rootProject.name = "${rootProjectName}"
"""
    }

    protected String getRootProjectName() {
        'app'
    }

    protected NativeBinaryFixture fixture(String path) {
        fixture(file(path))
    }

    protected NativeBinaryFixture fixture(TestFile binary) {
        new NativeBinaryFixture(binary, AvailableToolChains.defaultToolChain)
    }

    protected TestFile exe(String str) {
        file(OperatingSystem.current().getExecutableName(str))
    }

    protected TestFile sharedLib(String str) {
        file(OperatingSystem.current().getSharedLibraryName(str))
    }

    protected TestFile staticLib(String str) {
        file(OperatingSystem.current().getStaticLibraryName(str))
    }

    protected TestFile xctest(String str) {
        file(str + ".xctest")
    }

    protected XcodeProjectPackage xcodeProject(String path) {
        xcodeProject(file(path))
    }

    protected XcodeProjectPackage xcodeProject(TestFile bundle) {
        new XcodeProjectPackage(bundle)
    }

    protected XcodeProjectPackage getRootXcodeProject() {
        xcodeProject("${rootProjectName}.xcodeproj")
    }

    protected XcodeWorkspacePackage xcodeWorkspace(String path) {
        xcodeWorkspace(file(path))
    }

    protected XcodeWorkspacePackage xcodeWorkspace(TestFile bundle) {
        new XcodeWorkspacePackage(bundle)
    }

    protected XcodeWorkspacePackage getRootXcodeWorkspace() {
        xcodeWorkspace("${rootProjectName}.xcworkspace")
    }

    protected XcodebuildExecutor getXcodebuild() {
        // Gradle needs to be isolated so the xcodebuild does not leave behind daemons
        assert executer.distribution.gradleHomeDir != null
        assert !executer.usesSharedDaemons()
        new XcodebuildExecutor(testDirectory)
    }

    void useXcodebuildTool() {
        executer.requireDaemon().requireIsolatedDaemons()

        def initScript = file("init.gradle")
        initScript << IdeCommandLineUtil.generateGradleProbeInitFile('xcode', 'xcodebuild')

        executer.beforeExecute({
            usingInitScript(initScript)
        })
    }

    // TODO: Use AbstractInstalledToolChainIntegrationSpec instead once Xcode test are sorted out
    void requireSwiftToolChain() {
        toolChain = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC)
        assumeTrue(toolChain != null)

        File initScript = file("init.gradle") << """
            allprojects { p ->
                apply plugin: ${toolChain.pluginClass}

                model {
                      toolChains {
                        ${toolChain.buildScriptConfig}
                      }
                }
            }
        """
        executer.beforeExecute({
            usingInitScript(initScript)
        })
    }

    // TODO: Use @RequiresInstalledToolChain instead once Xcode test are sorted out
    void assumeSwiftCompilerVersion(SwiftVersion swiftVersion) {
        assert toolChain != null, "You need to specify Swift tool chain requirement with 'requireSwiftToolChain()'"
        assumeTrue(toolChain.version.major == swiftVersion.version)
    }

    // TODO: Use @RequiresInstalledToolChain instead once Xcode test are sorted out
    void assumeSwiftCompilerSupportsLanguageVersion(SwiftVersion swiftVersion) {
        assert toolChain != null, "You need to specify Swift tool chain requirement with 'requireSwiftToolChain()'"
        assumeTrue((toolChain.version.major == 5 && swiftVersion.version in [5, 4]) || (toolChain.version.major == 4 && swiftVersion.version in [4, 3]) || (toolChain.version.major == 3 && swiftVersion.version == 3))
    }

    void assertTargetIsUnitTest(ProjectFile.PBXTarget target, String expectedProductName) {
        target.assertIsUnitTest()
        target.assertProductNameEquals(expectedProductName)
        assert target.name == "$expectedProductName"
        assert target.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE, DefaultXcodeProject.TEST_DEBUG]
        assert target.buildConfigurationList.buildConfigurations.every {
            it.buildSettings.SWIFT_INCLUDE_PATHS == '"' + file('build/modules/main/debug').absolutePath + '"'
        }
    }

    void assertTargetIsDynamicLibrary(ProjectFile.PBXTarget target, String expectedProductName, String expectedBinaryName = expectedProductName) {
        target.assertIsDynamicLibrary()
        target.assertProductNameEquals(expectedProductName)
        target.assertSupportedArchitectures(archName == 'aarch64' ? MachineArchitecture.ARM64 : MachineArchitecture.X86_64)
        assert target.name == expectedProductName
        assert target.productReference.path == sharedLib("build/lib/main/debug/$expectedBinaryName").absolutePath
        assert target.buildArgumentsString == '-Porg.gradle.internal.xcode.bridge.ACTION="${ACTION}" -Porg.gradle.internal.xcode.bridge.PRODUCT_NAME="${PRODUCT_NAME}" -Porg.gradle.internal.xcode.bridge.CONFIGURATION="${CONFIGURATION}" -Porg.gradle.internal.xcode.bridge.BUILT_PRODUCTS_DIR="${BUILT_PRODUCTS_DIR}" :_xcode__${ACTION}_${PRODUCT_NAME}_${CONFIGURATION}'

        def expectedArchitecture = archName == 'aarch64' ? 'arm64e' : 'x86_64'
        assert target.buildConfigurationList.buildConfigurations[0].name == DefaultXcodeProject.BUILD_DEBUG
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.ARCHS == expectedArchitecture
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/debug").absolutePath

        assert target.buildConfigurationList.buildConfigurations[1].name == DefaultXcodeProject.BUILD_RELEASE
        assert target.buildConfigurationList.buildConfigurations[1].buildSettings.ARCHS == expectedArchitecture
        assert target.buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/release/stripped").absolutePath
    }

    void assertTargetIsStaticLibrary(ProjectFile.PBXTarget target, String expectedProductName, String expectedBinaryName = expectedProductName) {
        target.assertIsStaticLibrary()
        target.assertProductNameEquals(expectedProductName)
        target.assertSupportedArchitectures(archName == 'aarch64' ? MachineArchitecture.ARM64 : MachineArchitecture.X86_64)
        assert target.name == expectedProductName
        assert target.productReference.path == staticLib("build/lib/main/debug/$expectedBinaryName").absolutePath
        assert target.buildArgumentsString == '-Porg.gradle.internal.xcode.bridge.ACTION="${ACTION}" -Porg.gradle.internal.xcode.bridge.PRODUCT_NAME="${PRODUCT_NAME}" -Porg.gradle.internal.xcode.bridge.CONFIGURATION="${CONFIGURATION}" -Porg.gradle.internal.xcode.bridge.BUILT_PRODUCTS_DIR="${BUILT_PRODUCTS_DIR}" :_xcode__${ACTION}_${PRODUCT_NAME}_${CONFIGURATION}'

        def expectedArchitecture = archName == 'aarch64' ? 'arm64e' : 'x86_64'
        assert target.buildConfigurationList.buildConfigurations[0].name == DefaultXcodeProject.BUILD_DEBUG
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.ARCHS == expectedArchitecture
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/debug").absolutePath

        assert target.buildConfigurationList.buildConfigurations[1].name == DefaultXcodeProject.BUILD_RELEASE
        assert target.buildConfigurationList.buildConfigurations[1].buildSettings.ARCHS == expectedArchitecture
        assert target.buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/release").absolutePath
    }

    void assertTargetIsTool(ProjectFile.PBXTarget target, String expectedProductName, String expectedBinaryName = expectedProductName) {
        target.assertIsTool()
        target.assertProductNameEquals(expectedProductName)
        target.assertSupportedArchitectures(archName == 'aarch64' ? MachineArchitecture.ARM64 : MachineArchitecture.X86_64)
        assert target.name == expectedProductName
        assert target.productReference.path == exe("build/install/main/debug/lib/$expectedBinaryName").absolutePath
        assert target.buildArgumentsString == '-Porg.gradle.internal.xcode.bridge.ACTION="${ACTION}" -Porg.gradle.internal.xcode.bridge.PRODUCT_NAME="${PRODUCT_NAME}" -Porg.gradle.internal.xcode.bridge.CONFIGURATION="${CONFIGURATION}" -Porg.gradle.internal.xcode.bridge.BUILT_PRODUCTS_DIR="${BUILT_PRODUCTS_DIR}" :_xcode__${ACTION}_${PRODUCT_NAME}_${CONFIGURATION}'

        def expectedArchitecture = archName == 'aarch64' ? 'arm64e' : 'x86_64'
        assert target.buildConfigurationList.buildConfigurations[0].name == DefaultXcodeProject.BUILD_DEBUG
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.ARCHS == expectedArchitecture
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("build/install/main/debug/lib").absolutePath

        assert target.buildConfigurationList.buildConfigurations[1].name == DefaultXcodeProject.BUILD_RELEASE
        assert target.buildConfigurationList.buildConfigurations[1].buildSettings.ARCHS == expectedArchitecture
        assert target.buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("build/install/main/release/lib").absolutePath
    }

    static List<TestFile> toFiles(Object includePath) {
        def includePathElements = Splitter.on('"').splitToList(String.valueOf(includePath))
        return includePathElements.grep({ !it.trim().empty }).collect { new TestFile(it) }
    }

    protected String getCurrentHostOperatingSystemFamilyDsl() {
        String osFamily = DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName()
        if (osFamily == OperatingSystemFamily.MACOS) {
            return "macOS"
        } else {
            return osFamily
        }
    }
}
