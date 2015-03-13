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

package org.gradle.api.internal.tasks.compile

import com.google.common.collect.Lists
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.USE_UNSHARED_COMPILER_TABLE_OPTION

class CommandLineJavaCompilerArgumentsGeneratorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tempDir

    CommandLineJavaCompilerArgumentsGenerator argsGenerator = new CommandLineJavaCompilerArgumentsGenerator()

    def "inlines arguments if they are short enough"() {
        def spec = createCompileSpec(25)

        when:
        def args = argsGenerator.generate(spec)
        then:
        Lists.newArrayList(args) == ["-J-Xmx256m", "-g", "-sourcepath", defaultEmptySourcePathRefFolder(), "-classpath", spec.classpath.asPath, *spec.source*.path, USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "creates arguments file if arguments get too long"() {
        def spec = createCompileSpec(100)
        def argsFile = tempDir.createFile("java-compiler-args.txt")

        when:
        def args = argsGenerator.generate(spec)

        then: "argument list only contains launcher args and reference to args file"
        Lists.newArrayList(args) == ["-J-Xmx256m", "@$argsFile"]
        println argsFile.text

        and: "args file contains remaining arguments (one per line, quoted)"
        argsFile.readLines() == ["-g", "-sourcepath", quote(defaultEmptySourcePathRefFolder()), "-classpath", quote("$spec.classpath.asPath"), *(spec.source*.path.collect { quote(it) }), USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    String defaultEmptySourcePathRefFolder() {
       return tempDir.testDirectory.file(JavaCompilerArgumentsBuilder.EMPTY_SOURCE_PATH_REF_DIR).absolutePath
    }

    def createCompileSpec(numFiles) {
        def sources = createFiles(numFiles)
        def classpath = createFiles(numFiles)
        def spec = new DefaultJavaCompileSpec()
        spec.compileOptions = new CompileOptions()
        spec.compileOptions.forkOptions.memoryMaximumSize = "256m"
        spec.source = new SimpleFileCollection(sources)
        spec.classpath = new SimpleFileCollection(classpath)
        spec.tempDir = tempDir.testDirectory
        spec
    }

    def createFiles(numFiles) {
        (1..numFiles).collect { new File("/foo bar/File$it") }
    }

    def quote(arg) {
        "\"${arg.replace("\\", "\\\\")}\""
    }
}
