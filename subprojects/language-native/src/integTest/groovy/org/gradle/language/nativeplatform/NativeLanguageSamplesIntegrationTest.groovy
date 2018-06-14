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
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.junit.rules.TestRule
import spock.lang.IgnoreIf

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class NativeLanguageSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule final TestNameTestDirectoryProvider testDirProvider = new TestNameTestDirectoryProvider()
    final Sample sample = new Sample(testDirProvider)

    @Rule
    public final TestRule rules = sample.runInSampleDirectory(executer)


    @UsesSample('native-binaries/assembler')
    @IgnoreIf({GradleContextualExecuter.parallel})
    def "assembler"() {
        when:
        run "installMainExecutable"

        then:
        nonSkippedTasks.count { it.startsWith(":assembleMainExecutable") } == 1
        executedAndNotSkipped ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(sample.dir.file("build/install/main")).exec().out == "5 + 7 = 12\n"
    }

    @UsesSample('native-binaries/c')
    def "c"() {
        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloC", ":linkHelloSharedLibrary", ":helloSharedLibrary",
            ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(sample.dir.file("build/install/main")).exec().out == "Hello world!"
    }

    @UsesSample('native-binaries/cpp')
    def "cpp"() {
        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloCpp", ":linkHelloSharedLibrary", ":helloSharedLibrary",
            ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(sample.dir.file("build/install/main")).exec().out == "Hello world!\n"
    }

    @UsesSample('native-binaries/objective-c')
    @RequiresInstalledToolChain(GCC_COMPATIBLE)
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "objectiveC"() {
        when:
        succeeds "installMainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainObjc", ":linkMainExecutable", ":mainExecutable"

        and:
        executable(sample.dir.file("build/exe/main/main")).exec().out == "Hello world!\n"
    }

    @UsesSample('native-binaries/objective-cpp')
    @RequiresInstalledToolChain(GCC_COMPATIBLE)
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "objectiveCpp"() {
        when:
        succeeds "installMainExecutable"

        then:
        executedAndNotSkipped ":compileMainExecutableMainObjcpp", ":linkMainExecutable", ":mainExecutable"

        and:
        executable(sample.dir.file("build/exe/main/main")).exec().out == "Hello world!\n"
    }

    @UsesSample('native-binaries/windows-resources')
    @RequiresInstalledToolChain(VISUALCPP)
    def "win rc"() {
        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloCpp", ":compileHelloSharedLibraryHelloRc",
            ":linkHelloSharedLibrary", ":helloSharedLibrary",
            ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(sample.dir.file("build/install/main")).exec().out == "Hello world!\n"

        when:
        executer.usingBuildScript(sample.dir.file('build-resource-only-dll.gradle'))
        run "helloResSharedLibrary"

        then:
        file(sample.dir.file("build/libs/helloRes/shared/helloRes.dll")).assertExists()
    }

    @UsesSample('native-binaries/custom-layout')
    def "custom layout"() {
        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloStaticLibraryHelloC", ":createHelloStaticLibrary", ":helloStaticLibrary",
            ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(sample.dir.file("build/install/main")).exec().out == "Hello world!"
    }

    @UsesSample('native-binaries/idl')
    def "idl"() {
        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":idl", ":compileMainExecutableMainC", ":compileMainExecutableMainIdlOutput",
            ":linkMainExecutable", ":mainExecutable"

        and:
        installation(sample.dir.file("build/install/main")).exec().out == "Hello from generated source!!\n"
    }

    @UsesSample('native-binaries/pre-compiled-headers')
    def "pch"() {
        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":generateHelloCppPrefixHeaderFile", ":compileHelloSharedLibraryCppPreCompiledHeader",
            ":linkHelloSharedLibrary", ":helloSharedLibrary",
            ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation(sample.dir.file("build/install/main")).exec().out == "Hello world!\n"
    }
}
