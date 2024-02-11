/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativeplatform.fixtures

import org.gradle.api.internal.file.TestFiles
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.SourceFile
import org.gradle.integtests.fixtures.compatibility.MultiVersionTestCategory
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.time.Time
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

/**
 * Runs a test separately for each installed tool chain.
 */
@NativeToolchainTest
@MultiVersionTestCategory
@Requires(UnitTestPreconditions.NotMacOs)
abstract class AbstractInstalledToolChainIntegrationSpec extends AbstractIntegrationSpec implements HostPlatform {
    static AvailableToolChains.InstalledToolChain toolChain
    File initScript

    AvailableToolChains.InstalledToolChain getToolchainUnderTest() { toolChain }

    def setup() {
        initScript = file("init.gradle") << """
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
            toolChain.configureExecuter(it)
        })
    }

    String executableName(Object path) {
        return path + OperatingSystem.current().getExecutableSuffix()
    }

    String getExecutableExtension() {
        def suffix = OperatingSystem.current().executableSuffix
        return suffix.empty ? "" : suffix.substring(1)
    }

    ExecutableFixture executable(Object path) {
        return toolChain.executable(file(path))
    }

    LinkerOptionsFixture linkerOptionsFor(String taskName, TestFile projectDir = testDirectory) {
        return toolChain.linkerOptionsFor(projectDir.file("build/tmp/$taskName/options.txt"))
    }

    TestFile objectFile(Object path) {
        return toolChain.objectFile(file(path))
    }

    String withLinkLibrarySuffix(Object path) {
        return path + (toolChain.visualCpp ? OperatingSystem.current().linkLibrarySuffix : OperatingSystem.current().sharedLibrarySuffix)
    }

    String linkLibraryName(Object path) {
        return toolChain.visualCpp ? OperatingSystem.current().getLinkLibraryName(path.toString()) : OperatingSystem.current().getSharedLibraryName(path.toString())
    }

    String getLinkLibrarySuffix() {
        return toolChain.visualCpp ? OperatingSystem.current().linkLibrarySuffix.substring(1) : OperatingSystem.current().sharedLibrarySuffix.substring(1)
    }

    String staticLibraryName(Object path) {
        return OperatingSystem.current().getStaticLibraryName(path.toString())
    }

    String withStaticLibrarySuffix(Object path) {
        return path + OperatingSystem.current().staticLibrarySuffix
    }

    String getStaticLibraryExtension() {
        return OperatingSystem.current().staticLibrarySuffix.substring(1)
    }

    String withSharedLibrarySuffix(Object path) {
        return path + OperatingSystem.current().sharedLibrarySuffix
    }

    String sharedLibraryName(Object path) {
        return OperatingSystem.current().getSharedLibraryName(path.toString())
    }

    String getSharedLibraryExtension() {
        return OperatingSystem.current().sharedLibrarySuffix.substring(1)
    }

    SharedLibraryFixture sharedLibrary(Object path) {
        return toolChain.sharedLibrary(file(path))
    }

    StaticLibraryFixture staticLibrary(Object path) {
        return toolChain.staticLibrary(file(path))
    }

    NativeBinaryFixture resourceOnlyLibrary(Object path) {
        return toolChain.resourceOnlyLibrary(file(path))
    }

    NativeBinaryFixture machOBundle(Object path) {
        return new NativeBinaryFixture(file(path), toolChain)
    }

    def objectFileFor(File sourceFile, String rootObjectFilesDir = "build/objs/main/main${sourceType}") {
        return intermediateFileFor(sourceFile, rootObjectFilesDir, OperatingSystem.current().isWindows() ? ".obj" : ".o")
    }

    def intermediateFileFor(File sourceFile, String intermediateFilesDir, String intermediateFileSuffix) {
        File intermediateFile = new CompilerOutputFileNamingSchemeFactory(TestFiles.resolver(testDirectory)).create()
            .withObjectFileNameSuffix(intermediateFileSuffix)
            .withOutputBaseFolder(file(intermediateFilesDir))
            .map(file(sourceFile))
        return file(getTestDirectory().toURI().relativize(intermediateFile.toURI()))
    }

    List<NativeBinaryFixture> objectFiles(def sourceElement, String rootObjectFilesDir = "build/obj/${sourceElement.sourceSetName}/debug") {
        List<NativeBinaryFixture> result = new ArrayList<NativeBinaryFixture>()

        String sourceSetName = sourceElement.getSourceSetName()
        for (SourceFile sourceFile : sourceElement.getFiles()) {
            def swiftFile = file("src", sourceSetName, sourceFile.path, sourceFile.name)
            result.add(new NativeBinaryFixture(objectFileFor(swiftFile, rootObjectFilesDir), toolChain))
        }

        return result
    }

    boolean isNonDeterministicCompilation() {
        // Visual C++ compiler embeds a timestamp in every object file, and ASLR is non-deterministic
        toolChain.visualCpp || objectiveCWithAslr
    }

    // compiling Objective-C and Objective-Cpp with clang generates
    // random different object files (related to ASLR settings)
    // We saw this behaviour only on linux so far.
    // 2021-4-15: recent GCC also enable ASLR by default:
    // https://wiki.ubuntu.com/Security/Features
    boolean isObjectiveCWithAslr() {
        return (sourceType == "Objc" || sourceType == "Objcpp") &&
            OperatingSystem.current().isLinux() &&
            (toolChain.displayName.startsWith("clang") ||
                // GCC 9 or later
                toolChain.displayName ==~ /gcc (9|\d\d+).*/)
    }

    protected void maybeWait() {
        if (toolChain.visualCpp) {
            def now = Time.clock().currentTime
            def nextSecond = now % 1000
            Thread.sleep(1200 - nextSecond)
        }
    }

    protected String getCurrentOsFamilyName() {
        DefaultNativePlatform.currentOperatingSystem.toFamilyName()
    }

    protected String getCurrentArchitecture() {
        return DefaultNativePlatform.currentArchitecture.name
    }
}
