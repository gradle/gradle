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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification


class LinkExeLinkerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def argsTransformer = new LinkExeLinker.LinkerArgsTransformer()

    def "generates linker args for shared library"() {
        def dllFile = tmpDir.file("dll/out.dll")
        def libFile = tmpDir.file("import/out.lib")
        def objFile = tmpDir.file("obj/in.obj")
        def libInFile = tmpDir.file("libs/in.lib")
        def spec = Stub(SharedLibraryLinkerSpec)
        spec.outputFile >> dllFile
        spec.importLibrary >> libFile
        spec.objectFiles >> [objFile]
        spec.libraries >> [libInFile]

        expect:
        argsTransformer.transform(spec) == [
            "/OUT:${dllFile}",
            "/NOLOGO",
            "/DLL",
            "/IMPLIB:${libFile}",
            objFile,
            libInFile
        ].collect { it.toString() }
    }

    def "generates linker args for executable"() {
        def exeFile = tmpDir.file("exe/out.exe")
        def objFile = tmpDir.file("obj/in.obj")
        def libInFile = tmpDir.file("libs/in.lib")
        def spec = Stub(LinkerSpec)
        spec.outputFile >> exeFile
        spec.objectFiles >> [objFile]
        spec.libraries >> [libInFile]

        expect:
        argsTransformer.transform(spec) == [
            "/OUT:${exeFile}",
            "/NOLOGO",
            objFile,
            libInFile
        ].collect { it.toString() }
    }
}
