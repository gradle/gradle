/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.nativeplatform

import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.modes.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import org.gradle.test.preconditions.TestEnvironmentPreconditions

import org.junit.Rule

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.SUPPORTS_32_AND_64
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP

@Requires(TestEnvironmentPreconditions.CanInstallExecutable)
class NativeLanguageSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule final TestNameTestDirectoryProvider testDirProvider = new TestNameTestDirectoryProvider(getClass())
    @Rule public final Sample assembler = sample(testDirProvider, 'assembler')
    @Rule public final Sample c = sample(testDirProvider, 'c')
    @Rule public final Sample cpp = sample(testDirProvider, 'cpp')
    @Rule public final Sample objectiveC = sample(testDirProvider, 'objective-c')
    @Rule public final Sample objectiveCpp = sample(testDirProvider, 'objective-cpp')
    @Rule public final Sample customLayout = sample(testDirProvider, 'custom-layout')
    @Rule public final Sample windowsResources = sample(testDirProvider, 'windows-resources')
    @Rule public final Sample idl = sample(testDirProvider, 'idl')
    @Rule public final Sample cunit = sample(testDirProvider, 'cunit')
    @Rule public final Sample pch = sample(testDirProvider, 'pre-compiled-headers')

    private static final String CPP = "org.gradle.language.cpp.plugins.CppLangPlugin"
    private static final String C = "org.gradle.language.c.plugins.CLangPlugin"
    private static final String OBJECTIVE_C = "org.gradle.language.objectivec.plugins.ObjectiveCLangPlugin"
    private static final String OBJECTIVE_CPP = "org.gradle.language.objectivecpp.plugins.ObjectiveCppLangPlugin"
    private static final String ASSEMBLER = "org.gradle.language.assembler.plugins.AssemblerLangPlugin"
    private static final String WINDOWS_RESOURCES = "org.gradle.language.rc.plugins.WindowsResourceScriptPlugin"

    private static Sample sample(TestDirectoryProvider testDirectoryProvider, String name) {
        return new Sample(testDirectoryProvider, "integration-tests/native-binaries/${name}/groovy", name)
    }

    /**
     * The samples apply the deprecated rule-based/software model native plugins, which nag once per applied
     * rule source plugin. The set of base plugins is always the same; the language plugins vary per sample.
     * Expectations are cleared after each build invocation, so this must be called before each one.
     */
    private void expectSoftwareModelDeprecations(String... languagePlugins) {
        def plugins = [
            "org.gradle.platform.base.plugins.ComponentBasePlugin",
            "org.gradle.language.base.plugins.LanguageBasePlugin",
            "org.gradle.platform.base.plugins.BinaryBasePlugin",
            "org.gradle.language.base.plugins.ComponentModelBasePlugin",
            "org.gradle.nativeplatform.plugins.NativeComponentModelPlugin",
        ] + (languagePlugins as List)
        plugins.each { plugin ->
            executer.expectDocumentedDeprecationWarning("The ${plugin} plugin has been deprecated. This is scheduled to be removed in Gradle 10. Rule-based/software model plugins are no longer supported. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_software_model")
        }
    }

    @RequiresInstalledToolChain(SUPPORTS_32_AND_64)
    def "assembler"() {
        given:
        sample assembler

        when:
        expectSoftwareModelDeprecations(ASSEMBLER, C)
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(assembler.dir.file("build/install/main")).exec().out == "5 + 7 = 12\n"
    }

    def "c"() {
        given:
        sample c

        when:
        expectSoftwareModelDeprecations(C)
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloC", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(c.dir.file("build/install/main")).exec().out == "Hello world!"
    }

    def "cpp"() {
        given:
        sample cpp

        when:
        expectSoftwareModelDeprecations(CPP)
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloCpp", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(cpp.dir.file("build/install/main")).exec().out == "Hello world!\n"
    }

    @RequiresInstalledToolChain(GCC_COMPATIBLE)
    @Requires(OsTestPreconditions.NotWindows)
    def "objectiveC"() {
        given:
        sample objectiveC

        when:
        expectSoftwareModelDeprecations(OBJECTIVE_C)
        succeeds "installMainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainObjc", ":linkMainExecutable", ":mainExecutable"

        and:
        executable(objectiveC.dir.file("build/exe/main/main")).exec().out == "Hello world!\n"
    }

    @RequiresInstalledToolChain(GCC_COMPATIBLE)
    @Requires(OsTestPreconditions.NotWindows)
    def "objectiveCpp"() {
        given:
        sample objectiveCpp

        when:
        expectSoftwareModelDeprecations(OBJECTIVE_CPP)
        succeeds "installMainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainObjcpp", ":linkMainExecutable", ":mainExecutable"

        and:
        executable(objectiveCpp.dir.file("build/exe/main/main")).exec().out == "Hello world!\n"
    }

    @RequiresInstalledToolChain(VISUALCPP)
    @ToBeFixedForConfigurationCache
    def "win rc"() {
        given:
        System.err.println(windowsResources.dir.absolutePath)
        sample windowsResources

        when:
        expectSoftwareModelDeprecations(CPP, WINDOWS_RESOURCES)
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloCpp", ":compileHelloSharedLibraryHelloRc",
                              ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(windowsResources.dir.file("build/install/main")).exec().out == "Hello world!\n"

        when:
        inDirectory(windowsResources.dir.file('only-dll'))
        expectSoftwareModelDeprecations(WINDOWS_RESOURCES)
        run "helloResSharedLibrary"

        then:
        file(windowsResources.dir.file("only-dll/build/libs/helloRes/shared/helloRes.dll")).assertExists()
    }

    def "custom layout"() {
        given:
        sample customLayout

        when:
        expectSoftwareModelDeprecations(CPP, C)
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloStaticLibraryHelloC", ":createHelloStaticLibrary", ":helloStaticLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(customLayout.dir.file("build/install/main")).exec().out == "Hello world!"
    }

    def "idl"() {
        given:
        sample idl

        when:
        expectSoftwareModelDeprecations(C)
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":idl", ":compileMainExecutableMainC", ":compileMainExecutableMainIdlOutput",
                              ":linkMainExecutable", ":mainExecutable"

        and:
        installation(idl.dir.file("build/install/main")).exec().out == "Hello from generated source!!\n"
    }

    @ToBeFixedForConfigurationCache
    def "pch"() {
        given:
        sample pch

        when:
        expectSoftwareModelDeprecations(CPP)
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":generateHelloCppPrefixHeaderFile", ":compileHelloSharedLibraryCppPreCompiledHeader",
                              ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(pch.dir.file("build/install/main")).exec().out == "Hello world!\n"
    }
}
