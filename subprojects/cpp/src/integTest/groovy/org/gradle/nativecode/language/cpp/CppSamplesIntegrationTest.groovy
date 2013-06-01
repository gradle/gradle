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
    @Rule public final Sample exewithlib = new Sample(temporaryFolder, 'cpp/exewithlib')
    @Rule public final Sample dependencies = new Sample(temporaryFolder, 'cpp/dependencies')
    @Rule public final Sample exe = new Sample(temporaryFolder, 'cpp/exe')
    @Rule public final Sample library = new Sample(temporaryFolder, 'cpp/library')

    def "exe with lib"() {
        given:
        sample exewithlib

        when:
        run "installMainExecutable"

        then:
        ":exe:mainExecutable" in executedTasks

        and:
        sharedLibrary("cpp/exewithlib/lib/build/binaries/mainSharedLibrary/lib").isFile()
        executable("cpp/exewithlib/exe/build/binaries/mainExecutable/exe").isFile()
        normaliseLineSeparators(executable("cpp/exewithlib/exe/build/install/mainExecutable/exe").exec().out) == "Hello, World!\n"
    }

    // Does not work on windows, due to GRADLE-2118
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "dependencies"() {
        when:
        sample dependencies
        run ":lib:uploadArchives"

        then:
        sharedLibrary("cpp/dependencies/lib/build/binaries/mainSharedLibrary/lib").isFile()
        file("cpp/dependencies/lib/build/repo/some-org/some-lib/1.0/some-lib-1.0-so.so").isFile()

        when:
        sample dependencies
        run ":exe:uploadArchives"
        
        then:
        ":exe:mainExtractHeaders" in nonSkippedTasks
        ":exe:mainExecutable" in nonSkippedTasks
        
        and:
        executable("cpp/dependencies/exe/build/binaries/mainExecutable/exe").isFile()
        file("cpp/dependencies/exe/build/repo/dependencies/exe/1.0/exe-1.0.exe").exists()
    }
    
    def "exe"() {
        given:
        sample exe
        
        when:
        run "installMain"
        
        then:
        ":mainExecutable" in nonSkippedTasks
        
        and:
        normaliseLineSeparators(executable("cpp/exe/build/binaries/mainExecutable/exe").exec().out) == "Hello, World!\n"
        normaliseLineSeparators(executable("cpp/exe/build/install/mainExecutable/exe").exec().out) == "Hello, World!\n"
    }

    def "library"() {
        when:
        sample library
        run "installEnglishExecutable"

        then:
        executedAndNotSkipped ":compileEnglishExecutable", ":englishExecutable", ":compileHelloEnglishSharedLibrary", ":helloEnglishSharedLibrary"

        and:
        executable("cpp/library/build/binaries/englishExecutable/english").assertExists()
        sharedLibrary("cpp/library/build/binaries/helloEnglishSharedLibrary/helloEnglish").assertExists()

        and:
        normaliseLineSeparators(executable("cpp/library/build/install/englishExecutable/english").exec().out) == "Hello world!\n"

        when:
        sample library
        run "installFrenchExecutable"

        then:
        executedAndNotSkipped ":compileFrenchExecutable", ":frenchExecutable", ":compileHelloFrenchStaticLibrary", ":helloFrenchStaticLibrary"

        and:
        executable("cpp/library/build/binaries/frenchExecutable/french").assertExists()
        staticLibrary("cpp/library/build/binaries/helloFrenchStaticLibrary/helloFrench").assertExists()

        and:
        normaliseLineSeparators(executable("cpp/library/build/install/frenchExecutable/french").exec().out) == "Bonjour monde!\n"
    }
    
}