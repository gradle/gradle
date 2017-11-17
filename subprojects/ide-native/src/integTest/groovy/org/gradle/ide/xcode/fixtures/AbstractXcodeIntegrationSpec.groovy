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

import org.gradle.ide.xcode.internal.DefaultXcodeProject
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractorOsConfig
import org.gradle.test.fixtures.file.TestFile

class AbstractXcodeIntegrationSpec extends AbstractIntegrationSpec {
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

    protected TestFile exe(String str) {
        file(OperatingSystem.current().getExecutableName(str))
    }

    protected TestFile sharedLib(String str) {
        file(OperatingSystem.current().getSharedLibraryName(str))
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

    protected XcodebuildExecuter getXcodebuild() {
        // Gradle needs to be isolated so the xcodebuild does not leave behind daemons
        assert executer.isRequiresGradleDistribution()
        assert !executer.usesSharedDaemons()
        new XcodebuildExecuter(testDirectory)
    }

    void useXcodebuildTool() {
        executer.requireGradleDistribution().requireIsolatedDaemons()

        buildFile << '''
            gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL
            Properties gatherEnvironment() {
                Properties properties = new Properties()
                properties.JAVA_HOME = String.valueOf(System.getenv('JAVA_HOME'))
                properties.GRADLE_USER_HOME = String.valueOf(gradle.gradleUserHomeDir.absolutePath)
                properties.GRADLE_OPTS = String.valueOf(System.getenv('GRADLE_OPTS'))
                return properties
            }
            
            void assertEquals(key, expected, actual) {
                assert expected[key] == actual[key]
                if (expected[key] != actual[key]) {
                    throw new GradleException("""
Environment's $key did not match! 
Expected: ${expected[key]} 
Actual: ${actual[key]} 
""")
                }
            }
            
            def gradleEnvironment = file("gradle-environment")
            def xcodeTask = tasks.findByName('xcode')
            if (xcodeTask) {
                xcodeTask.doLast {
                    def writer = gradleEnvironment.newOutputStream()
                    gatherEnvironment().store(writer, null)
                    writer.close()
                }
            }
            gradle.buildFinished {
                if (!gradleEnvironment.exists()) {
                    throw new GradleException("could not determine if xcodebuild is using the correct environment, did xcode task run?")
                } else {
                    def expectedEnvironment = new Properties()
                    expectedEnvironment.load(gradleEnvironment.newInputStream())
                    
                    def actualEnvironment = gatherEnvironment()
                    
                    assertEquals('JAVA_HOME', expectedEnvironment, actualEnvironment)
                    assertEquals('GRADLE_USER_HOME', expectedEnvironment, actualEnvironment)
                    assertEquals('GRADLE_OPTS', expectedEnvironment, actualEnvironment)
                }
            }
        '''
    }

    void assertTargetIsUnitTest(ProjectFile.PBXTarget target, String expectedProductName) {
        target.assertIsUnitTest()
        assert target.productName == expectedProductName
        assert target.name == "$expectedProductName XCTestBundle"
        assert target.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE, DefaultXcodeProject.TEST_DEBUG]
        assert target.buildConfigurationList.buildConfigurations.every { it.buildSettings.PRODUCT_NAME == expectedProductName }
        assert target.buildConfigurationList.buildConfigurations.every {
            it.buildSettings.SWIFT_INCLUDE_PATHS == '"' + file('build/modules/main/debug').absolutePath + '"'
        }
        assertNotUnitTestBuildSettings(target.buildConfigurationList.buildConfigurations[0].buildSettings)
        assertNotUnitTestBuildSettings(target.buildConfigurationList.buildConfigurations[1].buildSettings)
        assertUnitTestBuildSettings(target.buildConfigurationList.buildConfigurations[2].buildSettings)
    }

    void assertTargetIsDynamicLibrary(ProjectFile.PBXTarget target, String expectedProductName, String expectedBinaryName = expectedProductName) {
        target.assertIsDynamicLibrary()
        assert target.productName == expectedProductName
        assert target.name == "$expectedProductName SharedLibrary"
        assert target.productReference.path == sharedLib("build/lib/main/debug/$expectedBinaryName").absolutePath
        assert target.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]
        assert target.buildConfigurationList.buildConfigurations.every { it.buildSettings.PRODUCT_NAME == expectedProductName }
        assertNotUnitTestBuildSettings(target.buildConfigurationList.buildConfigurations[0].buildSettings)
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/debug").absolutePath
        assertNotUnitTestBuildSettings(target.buildConfigurationList.buildConfigurations[1].buildSettings)
        assert target.buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/release").absolutePath
    }

    void assertTargetIsTool(ProjectFile.PBXTarget target, String expectedProductName, String expectedBinaryName = expectedProductName) {
        target.assertIsTool()
        assert target.productName == expectedProductName
        assert target.name == "$expectedProductName Executable"
        assert target.productReference.path == exe("build/exe/main/debug/$expectedBinaryName").absolutePath
        assert target.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]
        assert target.buildConfigurationList.buildConfigurations.every { it.buildSettings.PRODUCT_NAME == expectedProductName }
        assert target.buildArgumentsString == '-Porg.gradle.internal.xcode.bridge.ACTION="${ACTION}" -Porg.gradle.internal.xcode.bridge.PRODUCT_NAME="${PRODUCT_NAME}" -Porg.gradle.internal.xcode.bridge.CONFIGURATION="${CONFIGURATION}" -Porg.gradle.internal.xcode.bridge.BUILT_PRODUCTS_DIR="${BUILT_PRODUCTS_DIR}" :_xcode__${ACTION}_${PRODUCT_NAME}_${CONFIGURATION}'
        assertNotUnitTestBuildSettings(target.buildConfigurationList.buildConfigurations[0].buildSettings)
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("build/exe/main/debug").absolutePath
        assertNotUnitTestBuildSettings(target.buildConfigurationList.buildConfigurations[1].buildSettings)
        assert target.buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("build/exe/main/release").absolutePath
    }

    void assertUnitTestBuildSettings(Map<String, String> buildSettings) {
        assert buildSettings.OTHER_CFLAGS == "-help"
        assert buildSettings.OTHER_LDFLAGS == "-help"
        assert buildSettings.OTHER_SWIFT_FLAGS == "-help"
        assert buildSettings.SWIFT_INSTALL_OBJC_HEADER == "NO"
        assert buildSettings.SWIFT_OBJC_INTERFACE_HEADER_NAME == "\$(PRODUCT_NAME).h"
    }

    void assertNotUnitTestBuildSettings(Map<String, String> buildSettings) {
        assert buildSettings.OTHER_CFLAGS == null
        assert buildSettings.OTHER_LDFLAGS == null
        assert buildSettings.OTHER_SWIFT_FLAGS == null
        assert buildSettings.SWIFT_INSTALL_OBJC_HEADER == null
        assert buildSettings.SWIFT_OBJC_INTERFACE_HEADER_NAME == null
    }

    void assertTargetIsIndexer(ProjectFile.PBXTarget target, String expectedProductName, String swiftIncludes = null) {
        assert target.productName == expectedProductName
        assert target.name.startsWith("[INDEXING ONLY] $expectedProductName")
        assert target.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG]
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.PRODUCT_NAME == expectedProductName
        assert target.buildConfigurationList.buildConfigurations[0].buildSettings.SWIFT_INCLUDE_PATHS == swiftIncludes
    }

    void assertHasDebugSymbolsForSources(TestFile binary, SourceElement sourceElement) {
        def fixture = new NativeBinaryFixture(binary, null)
        assert fixture.binaryInfo.hasDebugSymbolsFor(sourceElement.files.findAll { !it.name.endsWith(".h") }.collect { it.name })
    }

    TestFile getSymbolFile(TestFile binary) {
        return file(binary.absolutePath + SymbolExtractorOsConfig.extension)
    }
}
