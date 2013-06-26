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
package org.gradle.nativecode.language.cpp
import org.gradle.integtests.fixtures.Sample
import org.gradle.nativecode.language.cpp.fixtures.AbstractBinariesIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.util.TextUtil.normaliseLineSeparators

class CppSamplesIntegrationTest extends AbstractBinariesIntegrationSpec {
    @Rule public final Sample cpp = new Sample(temporaryFolder, 'cpp/cpp')
    @Rule public final Sample cppExe = new Sample(temporaryFolder, 'cpp/cpp-exe')
    @Rule public final Sample cppLib = new Sample(temporaryFolder, 'cpp/cpp-lib')
    @Rule public final Sample multiProject = new Sample(temporaryFolder, 'cpp/multi-project')
    @Rule public final Sample variants = new Sample(temporaryFolder, 'cpp/variants')
    @Rule public final Sample dependencies = new Sample(temporaryFolder, 'cpp/dependencies')

    def "cpp"() {
        given:
        sample cpp
        
        when:
        run "installMainExecutable"
        
        then:
        executedAndNotSkipped ":compileHelloSharedLibrary", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutable", ":linkMainExecutable", ":mainExecutable"

        and:
        normaliseLineSeparators(executable("cpp/cpp/build/install/mainExecutable/main").exec().out) == "Hello world!\n"
    }

    def "exe"() {
        given:
        sample cppExe

        when:
        run "installMain"

        then:
        executedAndNotSkipped ":compileMainExecutable", ":linkMainExecutable", ":stripMainExecutable", ":mainExecutable"

        and:
        normaliseLineSeparators(executable("cpp/cpp-exe/build/binaries/mainExecutable/sampleExe").exec().out) == "Hello, World!\n"
        normaliseLineSeparators(executable("cpp/cpp-exe/build/install/mainExecutable/sampleExe").exec().out) == "Hello, World!\n"
    }

    def "lib"() {
        given:
        sample cppLib
        
        when:
        run "mainSharedLibrary"
        
        then:
        executedAndNotSkipped ":compileMainSharedLibrary", ":linkMainSharedLibrary", ":mainSharedLibrary"
        
        and:
        sharedLibrary("cpp/cpp-lib/build/binaries/mainSharedLibrary/sampleLib").assertExists()
        
        when:
        sample cppLib
        run "mainStaticLibrary"
        
        then:
        executedAndNotSkipped ":compileMainStaticLibrary", ":assembleMainStaticLibrary", ":mainStaticLibrary"
        
        and:
        staticLibrary("cpp/cpp-lib/build/binaries/mainStaticLibrary/sampleLib").assertExists()
    }

    def "variants"() {
        when:
        sample variants
        run "installEnglishExecutable"

        then:
        executedAndNotSkipped ":compileHelloEnglishSharedLibrary", ":linkHelloEnglishSharedLibrary", ":helloEnglishSharedLibrary"
        executedAndNotSkipped ":compileEnglishExecutable", ":linkEnglishExecutable", ":englishExecutable"

        and:
        executable("cpp/variants/build/binaries/englishExecutable/english").assertExists()
        sharedLibrary("cpp/variants/build/binaries/helloEnglishSharedLibrary/helloEnglish").assertExists()

        and:
        normaliseLineSeparators(executable("cpp/variants/build/install/englishExecutable/english").exec().out) == "Hello world!\n"

        when:
        sample variants
        run "installFrenchExecutable"

        then:
        executedAndNotSkipped ":compileHelloFrenchStaticLibrary", ":assembleHelloFrenchStaticLibrary", ":helloFrenchStaticLibrary"
        executedAndNotSkipped ":compileFrenchExecutable", ":linkFrenchExecutable", ":frenchExecutable"

        and:
        executable("cpp/variants/build/binaries/frenchExecutable/french").assertExists()
        staticLibrary("cpp/variants/build/binaries/helloFrenchStaticLibrary/helloFrench").assertExists()

        and:
        normaliseLineSeparators(executable("cpp/variants/build/install/frenchExecutable/french").exec().out) == "Bonjour monde!\n"
    }

    def multiProject() {
        given:
        sample multiProject

        when:
        run "installMainExecutable"

        then:
        ":exe:mainExecutable" in executedTasks

        and:
        sharedLibrary("cpp/multi-project/lib/build/binaries/mainSharedLibrary/lib").assertExists()
        executable("cpp/multi-project/exe/build/binaries/mainExecutable/exe").assertExists()
        normaliseLineSeparators(executable("cpp/multi-project/exe/build/install/mainExecutable/exe").exec().out) == "Hello, World!\n"
    }

    // Does not work on windows, due to GRADLE-2118
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "dependencies"() {
        when:
        sample dependencies
        run ":lib:uploadArchives"

        then:
        sharedLibrary("cpp/dependencies/lib/build/binaries/mainSharedLibrary/lib").assertExists()
        file("cpp/dependencies/lib/build/repo/some-org/some-lib/1.0/some-lib-1.0-so.so").isFile()

        when:
        sample dependencies
        run ":exe:uploadArchives"

        then:
        ":exe:mainExtractHeaders" in nonSkippedTasks
        ":exe:mainExecutable" in nonSkippedTasks

        and:
        executable("cpp/dependencies/exe/build/binaries/mainExecutable/exe").assertExists()
        file("cpp/dependencies/exe/build/repo/dependencies/exe/1.0/exe-1.0.exe").exists()
    }

}