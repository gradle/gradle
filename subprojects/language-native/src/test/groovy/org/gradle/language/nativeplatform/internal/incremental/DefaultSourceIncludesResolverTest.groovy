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
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.IncludeWithSimpleExpression
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.MacroWithSimpleExpression
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.ReturnFixedValueMacroFunction
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
        result.checkedLocations == [test]
    }

    def "ignores quoted include file that does not exist"() {
        given:
        def test1 = sourceDirectory.file("test.h")
        def test2 = systemIncludeDir.file("test.h")

        expect:
        def result = resolve(include('"test.h"'))
        result.complete
        result.files.empty
        result.checkedLocations == [test1, test2]
    }

    def "locates quoted includes in source directory"() {
        given:
        final header = sourceDirectory.createFile("test.h")

        expect:
        def result = resolve(include('"test.h"'))
        result.complete
        result.files == [header]
        result.checkedLocations == [header]
    }

    def "locates quoted includes relative to source directory"() {
        given:
        def header = sourceDirectory.createFile(path)

        expect:
        def result = resolve(include("\"${path}\""))
        result.complete
        result.files == [header]
        result.checkedLocations == [new File(sourceDirectory, path)] // not canonicalized

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
        result.checkedLocations == [header]
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
        result.files == [header1]
        result.checkedLocations == [header, header1]
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
        result.files == [header1]
        result.checkedLocations == [srcHeader, header, header1]
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
        result.files == [header1]
        result.checkedLocations == [srcHeader, header, header1]
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
        result.files == [header1]
        result.checkedLocations == [srcHeader, header, header1]
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
        result.files == [header1]
        result.checkedLocations == [srcHeader, header, header1]
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
        result.files == [includeFile1, includeFile2, includeFile3]
        result.checkedLocations == [includeFile1, includeFile2, includeFile3]
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
        result.files == [includeFile1, includeFile2, includeFile3]
        result.checkedLocations == [includeFile1, includeFile2, includeFile3]
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
        result.files == [includeFile]
        result.checkedLocations == [includeFile]
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
        macroFunctions << macroFunction("TEST", 1,'"test.h"')

        expect:
        def result = resolve(include('TEST()'))
        !result.complete
        result.files.empty
        result.checkedLocations.empty
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

    def include(String value) {
        return IncludeWithSimpleExpression.parse(value, false)
    }

    def macro(String name, String value) {
        def include = IncludeWithSimpleExpression.parse(value, false)
        new MacroWithSimpleExpression(name, include.type, include.value)
    }

    def macroFunction(String name, int parameters = 0, String value) {
        def include = IncludeWithSimpleExpression.parse(value, false)
        new ReturnFixedValueMacroFunction(name, parameters, include.type, include.value, [])
    }

    def unresolveableMacro(String name) {
        new UnresolveableMacro(name)
    }
}
