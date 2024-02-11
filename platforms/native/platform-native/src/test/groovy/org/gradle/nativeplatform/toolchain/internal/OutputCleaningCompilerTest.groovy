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

package org.gradle.nativeplatform.toolchain.internal


import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class OutputCleaningCompilerTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider(getClass())

    TestFile outputDir = tmpDirProvider.createDir("objs")

    def compilerOutputFileNamingSchemeFactory = new CompilerOutputFileNamingSchemeFactory(TestFiles.resolver(tmpDirProvider.testDirectory))
    def namingScheme = compilerOutputFileNamingSchemeFactory.create()

    private static final String O_EXT = ".o"

    NativeCompileSpec spec = Mock(NativeCompileSpec)
    Compiler delegateCompiler = Mock(Compiler)

    OutputCleaningCompiler cleanCompiler = new OutputCleaningCompiler<NativeCompileSpec>(delegateCompiler, compilerOutputFileNamingSchemeFactory, O_EXT);

    WorkResult workResult = Mock(WorkResult)
    List<TestFile> sourceFiles

    def setup() {
        _ * spec.objectFileDir >> outputDir
        _ * delegateCompiler.execute(_) >> { NativeCompileSpec spec ->
            List<File> sourceFiles = spec.getSourceFiles()
            sourceFiles.each{ inputFile ->
                createObjDummy(inputFile)
            }
            _ * workResult.getDidWork() >> !sourceFiles.isEmpty();
            return workResult
        }
    }

    def "deletes output files and according hash directory"() {
        setup:
        sourceFiles = Arrays.asList(tmpDirProvider.file("src/main/c/main.c"), tmpDirProvider.file("src/main/c/foo/main2.c"))
        when:
        compile(sourceFiles[0], sourceFiles[1])
        then:
        outputDir.listFiles().size() == 2

        when:
        compile(sourceFiles[0])
        then:
        objectFile(sourceFiles[0])
        !objectFile(sourceFiles[1])


        when:
        compile(sourceFiles[1])
        then:
        !objectFile(sourceFiles[0])
        objectFile(sourceFiles[1])
    }

    def "removes stale output when source file is moved"() {
        setup:
        def orgFile = tmpDirProvider.file("src/main/c/org/main.c")
        def movedFile = tmpDirProvider.file("src/main/c/moved/main.c")
        sourceFiles = [orgFile, movedFile]

        when:
        compile(orgFile)

        then:
        objectFile(orgFile)
        !objectFile(movedFile)

        when:
        compile(movedFile)
        then:
        !objectFile(orgFile)
        objectFile(movedFile)
    }

    def objectFile(TestFile testFile) {
        assert outputDir.listFiles().size() == 1
        assert outputDir.listFiles()[0].listFiles().size() == 1
        outputDir.listFiles()[0].listFiles()[0].text == testFile.absolutePath
    }

    def compile(TestFile... sourceToCompile) {
        List<TestFile> toCompile = Arrays.asList(sourceToCompile)
        List<TestFile> toRemove = sourceFiles - toCompile
        2 * spec.getSourceFiles() >> toCompile
        1 * spec.getRemovedSourceFiles() >> toRemove
        cleanCompiler.execute(spec)
    }

    def createObjDummy(File sourceFile) {
        def objectFile = new TestFile(namingScheme.withObjectFileNameSuffix(O_EXT).
            withOutputBaseFolder(outputDir).
            map(sourceFile))

        objectFile.touch()
        objectFile.text = sourceFile.absolutePath
    }
}
