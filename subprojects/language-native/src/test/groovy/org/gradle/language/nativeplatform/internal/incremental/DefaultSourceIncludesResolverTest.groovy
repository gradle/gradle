/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental

import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.RegexBackedCSourceParser
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.UnresolveableMacro
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultSourceIncludesResolverTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def testDirectory = temporaryFolder.testDirectory
    def sourceDirectory = testDirectory.createDir("sources")
    def systemIncludeDir = testDirectory.createDir("headers")
    def included
    def macros = []
    def macroFunctions = []
    def includePaths = [ systemIncludeDir ]

    def setup() {
        included = Mock(IncludeDirectives)
        included.getMacros() >> macros
        included.getMacrosFunctions() >> macroFunctions
    }

    protected TestFile getSourceFile() {
        sourceDirectory.file('source.c')
    }

    def resolve(Include include) {
        def macros = new MacroLookup()
        macros.append(sourceFile, included)
        return new DefaultSourceIncludesResolver(includePaths).resolveInclude(sourceFile, include, macros)
    }

    def "ignores system include file that does not exist"() {
        given:
        def test = systemIncludeDir.file("test.h")

        expect:
        def result = resolve(include('<test.h>'))
        result.complete
        result.files.empty
        result.checkedLocations as List == [test]
    }

    def "ignores quoted include file that does not exist"() {
        given:
        def test1 = sourceDirectory.file("test.h")
        def test2 = systemIncludeDir.file("test.h")

        expect:
        def result = resolve(include('"test.h"'))
        result.complete
        result.files.empty
        result.checkedLocations as List == [test1, test2]
    }

    def "locates quoted includes in source directory"() {
        given:
        final header = sourceDirectory.createFile("test.h")

        expect:
        def result = resolve(include('"test.h"'))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [header]
    }

    def "locates quoted includes relative to source directory"() {
        given:
        def header = sourceDirectory.createFile(path)

        expect:
        def result = resolve(include("\"${path}\""))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [new File(sourceDirectory, path)] // not canonicalized

        where:
        path << ["nested/test.h", "../sibling/test.h", "./test.h"]
    }

    def "does not locate system includes in source directory"() {
        given:
        sourceDirectory.file("system.h").createFile()
        def header = systemIncludeDir.file("system.h")

        expect:
        def result = resolve(include('<system.h>'))
        result.complete
        result.files.empty
        result.checkedLocations as List == [header]
    }

    def "locates system include in path"() {
        given:
        def header = systemIncludeDir.file("system.h")
        def includeDir1 = testDirectory.file("include1")
        def header1 = includeDir1.file("system.h").createFile()
        def includeDir2 = testDirectory.file("include2")
        includeDir2.file("system.h").createFile()

        includePaths << includeDir1 << includeDir2

        expect:
        def result = resolve(include('<system.h>'))
        result.complete
        result.files as List == [header1]
        result.checkedLocations as List == [header, header1]
    }

    def "locates quoted include in path"() {
        given:
        def srcHeader = sourceDirectory.file("header.h")
        def header = systemIncludeDir.file("header.h")
        def includeDir1 = testDirectory.file("include1")
        def header1 = includeDir1.file("header.h").createFile()
        def includeDir2 = testDirectory.file("include2")
        includeDir2.file("header.h").createFile()

        includePaths << includeDir1 << includeDir2

        expect:
        def result = resolve(include('"header.h"'))
        result.complete
        result.files as List == [header1]
        result.checkedLocations as List == [srcHeader, header, header1]
    }

    def "resolves macro include"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.file("test.h")
        def includeDir = testDirectory.file("include")
        def header1 = includeDir.createFile("test.h")
        includePaths << includeDir

        macros << macro("TEST", '"test.h"')
        macros << macro("IGNORE", "'broken'")
        macros << unresolveableMacro("IGNORE")

        expect:
        def result = resolve(include('TEST'))
        result.complete
        result.files as List == [header1]
        result.checkedLocations as List == [srcHeader, header, header1]
    }

    def "resolves macro function include"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.file("test.h")
        def includeDir = testDirectory.file("include")
        def header1 = includeDir.createFile("test.h")
        includePaths << includeDir

        macroFunctions << macroFunction("TEST", '"test.h"')
        macroFunctions << macroFunction("IGNORE", "'broken'")

        expect:
        def result = resolve(include('TEST()'))
        result.complete
        result.files as List == [header1]
        result.checkedLocations as List == [srcHeader, header, header1]
    }

    def "resolves nested macro include"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.file("test.h")
        def includeDir = testDirectory.file("include")
        def header1 = includeDir.createFile("test.h")
        includePaths << includeDir

        macros << macro("TEST", '"test.h"')
        macroFunctions << macroFunction("TEST1", "TEST")
        macros << macro("TEST2", "TEST1()")
        macros << macro("TEST3", "TEST2")
        macros << unresolveableMacro("IGNORE")

        expect:
        def result = resolve(include('TEST3'))
        result.complete
        result.files as List == [header1]
        result.checkedLocations as List == [srcHeader, header, header1]
    }

    def "resolves macro include once for each definition of the macro"() {
        given:
        def includeFile1 = sourceDirectory.createFile("test1.h")
        def includeFile2 = sourceDirectory.createFile("test2.h")
        def includeFile3 = sourceDirectory.createFile("test3.h")

        macros << macro("TEST", '"test1.h"')
        macros << macro("IGNORE", '"broken"')
        macros << macro("NESTED", '"test2.h"')
        macros << macro("NESTED", '"test3.h"')
        macros << macro("TEST", "NESTED")

        expect:
        def result = resolve(include('TEST'))
        result.complete
        result.files as List == [includeFile1, includeFile2, includeFile3]
        result.checkedLocations as List == [includeFile1, includeFile2, includeFile3]
    }

    def "resolves macro function include once for each definition of the function"() {
        given:
        def includeFile1 = sourceDirectory.createFile("test1.h")
        def includeFile2 = sourceDirectory.createFile("test2.h")
        def includeFile3 = sourceDirectory.createFile("test3.h")

        macroFunctions << macroFunction("TEST", '"test1.h"')
        macroFunctions << macroFunction("IGNORE", '"broken"')
        macroFunctions << macroFunction("NESTED", '"test2.h"')
        macroFunctions << macroFunction("NESTED", '"test3.h"')
        macroFunctions << macroFunction("TEST", "NESTED()")

        expect:
        def result = resolve(include('TEST()'))
        result.complete
        result.files as List == [includeFile1, includeFile2, includeFile3]
        result.checkedLocations as List == [includeFile1, includeFile2, includeFile3]
    }

    def "marks macro include as unresolved when target macro value cannot be resolved"() {
        given:
        macros << unresolveableMacro("TEST")
        macros << unresolveableMacro("NESTED")
        macros << macro("TEST", "NESTED")

        expect:
        def result = resolve(include('TEST'))
        !result.complete
        result.files.empty
        result.checkedLocations.empty
    }

    def "marks macro include as unresolved when any macro value cannot be resolved"() {
        given:
        def includeFile = sourceDirectory.createFile("test.h")

        macros << macro("TEST", '"test.h"')
        macros << macro("IGNORE", '"broken"')
        macros << unresolveableMacro("TEST")

        expect:
        def result = resolve(include('TEST'))
        !result.complete
        result.files as List == [includeFile]
        result.checkedLocations as List == [includeFile]
    }

    def "macro does not match macro function of the same name"() {
        given:
        macroFunctions << macroFunction("TEST", '"test.h"')

        expect:
        def result = resolve(include('TEST'))
        !result.complete
        result.files.empty
        result.checkedLocations.empty
    }

    def "macro function does not match macro of the same name"() {
        given:
        macros << macro("TEST", '"test.h"')

        expect:
        def result = resolve(include('TEST()'))
        !result.complete
        result.files.empty
        result.checkedLocations.empty
    }

    def "macro function does not match macro function with different number of parameters"() {
        given:
        macroFunctions << macroFunction("TEST1(X)", '"test.h"')
        macroFunctions << macroFunction("TEST2(X, Y)", '"test.h"')

        expect:
        def result = resolve(include('TEST1(A, B)'))
        !result.complete
        result.files.empty
        result.checkedLocations.empty

        def result2 = resolve(include('TEST2(A)'))
        !result2.complete
        result2.files.empty
        result2.checkedLocations.empty
    }

    def "provides an implicit empty parameter when macro function takes one parameter"() {
        given:
        def header = systemIncludeDir.createFile("test.h")
        macroFunctions << macroFunction("TEST1(X, Y)", '<test.h>')
        macroFunctions << macroFunction("TEST2(X)", '<test.h>')

        expect:
        def result = resolve(include('TEST1()'))
        !result.complete
        result.files.empty
        result.checkedLocations.empty

        def result2 = resolve(include('TEST2()'))
        result2.complete
        result2.files as List == [header]
        result2.checkedLocations as List == [header]
    }

    def "marks macro include as unresolved when there are no definitions of the macro"() {
        expect:
        def result = resolve(include('TEST'))
        !result.complete
        result.files.empty
        result.checkedLocations.empty
    }

    def "marks macro function include as unresolved when there are no definitions of the macro"() {
        expect:
        def result = resolve(include('TEST()'))
        !result.complete
        result.files.empty
        result.checkedLocations.empty
    }

    def "resolves token concatenation to a macro and then to file"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.createFile("test.h")

        macros << macro("TEST", 'FILE##NAME')
        macros << macro("FILENAME", '"test.h"')

        expect:
        def result = resolve(include('TEST'))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [srcHeader, header]
    }

    def "does not macro expand the arguments of token concatenation in macro"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.createFile("test.h")

        macros << macro("TEST", 'FILE##NAME')
        macros << unresolveableMacro("FILE")
        macros << unresolveableMacro("NAME")
        macros << macro("FILENAME", '"test.h"')

        expect:
        def result = resolve(include('TEST'))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [srcHeader, header]
    }

    def "resolves token concatenation inside macro function to macro then to file"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.createFile("test.h")

        macros << macro("FILENAME", '"test.h"')
        macroFunctions << macroFunction("TEST(X, Y)", "X##Y")

        expect:
        def result = resolve(include('TEST(FILE, NAME)'))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [srcHeader, header]
    }

    def "right-hand side of token concatenation can be empty"() {
        given:
        def header = systemIncludeDir.createFile("test.h")

        macros << macro("FILENAME", '<test.h>')
        macroFunctions << macroFunction("TEST(X, Y)", "X##Y")

        expect:
        def result = resolve(include('TEST(FILENAME, )'))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [header]
    }

    def "does not macro expand the arguments of token concatenation in macro function"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.createFile("test.h")

        macros << unresolveableMacro("FILE")
        macros << unresolveableMacro("NAME")
        macros << macro("FILENAME", '"test.h"')
        macroFunctions << macroFunction("TEST(X, Y)", "X##Y")

        expect:
        def result = resolve(include('TEST(FILE, NAME)'))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [srcHeader, header]
    }

    def "expands parameters of macro function that calls macro function that concatenates parameters to produce macro reference"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.createFile("test.h")

        macros << macro("A", 'FILE')
        macros << macro("C", 'NAME')
        macros << macro("FILENAME", '"test.h"')
        macroFunctions << macroFunction("B(X)", "X")
        macroFunctions << macroFunction("TEST2(X, Y)", "X##Y")
        macroFunctions << macroFunction("TEST(X, Y)", "TEST2(X, Y)")

        expect:
        def result = resolve(include('TEST(A, B(C))'))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [srcHeader, header]
    }

    def "expands parameters of macro function that calls macro function that concatenates parameters to produce macro function call"() {
        given:
        def srcHeader = sourceDirectory.file("test.h")
        def header = systemIncludeDir.createFile("test.h")

        macros << macro("FILE_NAME", '"test.h"')
        macroFunctions << macroFunction("FILENAME(X)", "FILE##X")
        macroFunctions << macroFunction("TEST2(X, Y)", "X##Y")
        macroFunctions << macroFunction("TEST(X, Y)", "TEST2(X, (Y))")

        expect:
        def result = resolve(include('TEST(FILENAME, _NAME)'))
        result.complete
        result.files as List == [header]
        result.checkedLocations as List == [srcHeader, header]
    }

    def include(String value) {
        def directives = new RegexBackedCSourceParser().parseSource(new StringReader("#include $value"))
        return directives.includesOnly.first()
    }

    def macro(String name, String value) {
        def directives = new RegexBackedCSourceParser().parseSource(new StringReader("#define ${name} $value"))
        return directives.macros.first()
    }

    def macroFunction(String name, String value) {
        def directives = new RegexBackedCSourceParser().parseSource(new StringReader("#define ${name}${name.contains('(') ? "" : "()"} $value"))
        return directives.macrosFunctions.first()
    }

    def unresolveableMacro(String name) {
        new UnresolveableMacro(name)
    }
}
