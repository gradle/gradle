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

package org.gradle.language.nativeplatform.internal.incremental

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.IncludeDirectivesSerializer
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.RegexBackedCSourceParser
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule

/**
 * An integration test that covers source parsing and include resolution plus persistence of parsed state.
 */
@UsesNativeServices
class SourceParseAndResolutionTest extends SerializerSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def includeDir = tmpDir.createDir("headers")
    def header = includeDir.createFile("hello.h")
    def sourceDir = tmpDir.createDir("src")
    def sourceFile = sourceDir.createFile("src.cpp")
    def resolver = new DefaultSourceIncludesResolver([includeDir], TestFiles.fileSystemAccess())
    def parser = new RegexBackedCSourceParser()
    def serializer = IncludeDirectivesSerializer.INSTANCE

    def "resolves macro with value that is a string constant"() {
        given:
        sourceFile << """
            #define HEADER "hello.h"
            #include HEADER
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro with value that is a system include"() {
        given:
        sourceFile << """
            #define HEADER <hello.h>
            #include HEADER
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro with value that is a macro reference"() {
        given:
        sourceFile << """
            #define HEADER2 <hello.h>
            #define HEADER HEADER2 // indirection
            #include HEADER
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro with value that is a macro function call with zero args"() {
        given:
        sourceFile << """
            #define HEADER2() <hello.h>
            #define HEADER HEADER2 ( ) // some comment
            #include HEADER
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro with value that is a macro function call with multiple args"() {
        given:
        sourceFile << """
            #define HEADER2(X, Y) <hello.h>
            #define A2(X) X
            #define HEADER HEADER2 (A1, A2("ignore.h")) // some comment
            #include HEADER
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro with value that is token concatenation that produces another macro"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define _NAME do not use this
            #define HEADER HEADER ## _NAME // replaced with HEADER_NAME then "hello.h"
            #include HEADER
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro with value that is multiple token concatenations that produces another macro"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER HEAD ## ER ## _ ## NAME // replaced with HEADER_NAME then "hello.h"
            #include HEADER
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro with multiple values once for each value"() {
        given:
        def header2 = includeDir.createFile("hello_linux.h")
        sourceFile << """
            #ifdef SOMETHING
            #define HEADER <hello.h>
            #else
            #define HEADER_LINUX <hello_linux.h>
            #define HEADER HEADER_LINUX
            #endif
            #include HEADER
        """

        expect:
        resolve() == [header, header2]
    }

    def "resolves each include once regardless of how many times it appears in the source file"() {
        given:
        sourceFile << """
            #include <hello.h>
            #include \\
                <hello.h>
            #include/* */<hello.h>
        """

        expect:
        resolve() == [header]
    }

    def "does not resolve macro with multiple tokens"() {
        given:
        sourceFile << """
            #define HEADER ${value}
            #include HEADER
        """

        expect:
        doesNotResolve('#include HEADER')

        where:
        value << ["12 + 6", "(p)"]
    }

    def "does not resolve macro with concatenation that produces unknown macro"() {
        given:
        sourceFile << """
            #define _NAME do not use this
            #define HEADER HEADER ## _NAME
            #include HEADER
        """

        expect:
        doesNotResolve('#include HEADER')
    }

    def "does not resolve include with unknown macro"() {
        given:
        sourceFile << """
            #include HEADER
        """

        expect:
        doesNotResolve('#include HEADER')
    }

    def "does not resolve include with concatenation expression"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #include HEADER ## _NAME
        """

        expect:
        doesNotResolve('#include HEADER##_NAME')
    }

    def "does not resolve include with multiple tokens"() {
        given:
        sourceFile << """
            #include ${value}
        """

        expect:
        doesNotResolve("#include ${value}")

        where:
        value << ['1 + 2', '"header.h" extra', '<header.h> extra', '(p)', '~']
    }

    def "resolves macro function with zero args with body that is a string constant"() {
        given:
        sourceFile << """
            #define HEADER() "hello.h"
            #include HEADER( )
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with zero args with body that is a system include"() {
        given:
        sourceFile << """
            #define HEADER() <hello.h>
            #include HEADER( )
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with zero args with body that is a macro"() {
        given:
        sourceFile << """
            #define _HEADER_ "hello.h"
            #define HEADER() _HEADER_
            #include HEADER( )
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with zero args with body that is a macro function call"() {
        given:
        sourceFile << """
            #define _HEADER_ "hello.h"
            #define HEADER2(X) X
            #define HEADER() HEADER2(_HEADER_)
            #include HEADER( )
        """

        expect:
        resolve() == [header]
    }

    def "does not resolve unknown macro function call"() {
        given:
        sourceFile << """
            #include HEADER( )
        """

        expect:
        doesNotResolve('#include HEADER()')
    }

    def "does not resolve macro function call with too many args"() {
        given:
        sourceFile << """
            #define HEADER() "hello.h"
            #include HEADER(X)
        """

        expect:
        doesNotResolve('#include HEADER(X)')
    }

    def "provides implicit empty arg for function call if missing"() {
        given:
        sourceFile << """
            #define HEADER2(X, Y, Z) "hello.h"
            #define HEADER(X) HEADER2(, , )
            #include HEADER()
        """

        expect:
        resolve() == [header]
    }

    def "ignores unresolvable macro function parameter if it is not used"() {
        given:
        sourceFile << """
            #define HEADER(A, B, C, D) "hello.h"
            #include HEADER(~, 12, unknown, ) // implicit empty arg after the ','
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg with body that is a string constant"() {
        given:
        sourceFile << """
            #define HEADER(X) "hello.h"
            #include HEADER(ignore)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns param and param is string constant"() {
        given:
        sourceFile << """
            #define HEADER(X) X
            #include HEADER("hello.h")
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns param and param is system path"() {
        given:
        sourceFile << """
            #define HEADER(X) X
            #include HEADER(<hello.h>)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns param and param is macro"() {
        given:
        sourceFile << """
            #define HEADER_ "hello.h"
            #define HEADER(X) X
            #include HEADER(HEADER_)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns param and param is macro function call with zero args"() {
        given:
        sourceFile << """
            #define HEADER1() "hello.h"
            #define HEADER2(X) X
            #include HEADER2(HEADER1())
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns param and param is macro function call with arg"() {
        given:
        sourceFile << """
            #define HEADER(X) X
            #include HEADER(HEADER("hello.h"))
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns param and param is string constant"() {
        given:
        sourceFile << """
            #define HEADER(X, Y, Z) Y
            #include HEADER(IGNORE, "hello.h", "ignore.h")
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns param and param is macro reference"() {
        given:
        sourceFile << """
            #define HEADER_ "hello.h"
            #define HEADER(X, Y, Z) Y
            #include HEADER(IGNORE, HEADER_, IGNORE)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns param and param is macro function call"() {
        given:
        sourceFile << """
            #define HEADER_(X) "hello.h"
            #define HEADER(X, Y, Z) Y
            #include HEADER(IGNORE, HEADER_(IGNORE()), IGNORE)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns macro function call with no args"() {
        given:
        sourceFile << """
            #define HEADER_() "hello.h"
            #define HEADER(X) HEADER_()
            #include HEADER(ignore)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns macro function call with arg that is parameter and param is quoted string"() {
        given:
        sourceFile << """
            #define HEADER_(X, Y) X
            #define HEADER(X) HEADER_(X, ignore)
            #include HEADER("hello.h")
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns macro function call with arg that is parameter and param is macro"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER_(X, Y) X
            #define HEADER(X) HEADER_(X, ignore)
            #include HEADER(HEADER_NAME)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns macro function call with nested arg that is parameter to produce macro"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER_(X, Y) Y
            #define HEADER__(X) X
            #define HEADER(X) HEADER__(HEADER_(HEADER__(ignore), X))
            #include HEADER(HEADER(HEADER_NAME))
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with arg that returns macro function call with arg that is other macro"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER_(X, Y) X
            #define HEADER(X) HEADER_(HEADER_NAME, ignore)
            #include HEADER(ignore)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns concatenation of tokens"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER_ do not use this
            #define NAME do not use this
            #define HEADER(X, Y) HEADER_ ## NAME
            #include HEADER(ignore, ignore) // replaced with HEADER_NAME then "hello.h"
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns concatenation of the args"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER_ do not use this
            #define NAME do not use this
            #define HEADER(X, Y) X ## Y
            #include HEADER(HEADER_, NAME) // replaced with HEADER_NAME then "hello.h"
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that concatenates empty right hand side to produce macro"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER(X, Y) X ## Y
            #include HEADER(HEADER_NAME,)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that concatenates empty right hand side to produce macro function"() {
        given:
        sourceFile << """
            #define HEADER_NAME(X) X
            #define HEADER(X, Y) X ## Y
            #include HEADER(HEADER_NAME("hello.h"),)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns concatenation of the args when arg is digit"() {
        given:
        sourceFile << """
            #define HEADER_123 "hello.h"
            #define HEADER(X, Y) X ## Y
            #include HEADER(HEADER_, 123)
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns concatenation of arg and token to produce reference to another macro"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER_ do not use this
            #define NAME do not use this
            #define HEADER(X, Y) Y ## NAME
            #include HEADER(ignore(), HEADER_) // replaced with HEADER_NAME then "hello.h"
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns concatenation of arg and token to produce a macro function call"() {
        given:
        sourceFile << """
            #define HEADER_NAME(X) "hello.h"
            #define HEADER_(X) HEADER_NAME ## X
            #define HEADER() HEADER_((z))
            #include HEADER() // replaced with HEADER_((z)) then HEADER_NAME(z) then "hello.h"
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns concatenation of arg and token to produce a macro function call where arg references macro parameter"() {
        given:
        sourceFile << """
            #define HEADER_NAME_2 "hello.h"
            #define HEADER_NAME(X) HEADER_NAME_ ## X
            #define HEADER_(X) HEADER_NAME ## X
            #define HEADER(X) HEADER_((X))
            #include HEADER(2) // replaced with HEADER_((2)) then HEADER_NAME(2) then HEADER_NAME_2 then "hello.h"
        """

        expect:
        resolve() == [header]
    }

    def "resolves macro function with multiple args that returns result of function that concatenates the args"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER_(X, Y) X ## Y
            #define HEADER(X, Y) HEADER_(X, Y)
            #define PREFIX HEADER_
            #define SUFFIX NAME
            #include HEADER(PREFIX, SUFFIX) // replaced with HEADER_(HEADER_, NAME) then HEADER_NAME then "hello.h"
        """

        expect:
        resolve() == [header]
    }

    def "can produce a macro function call by concatenating name and args passed as param"() {
        given:
        sourceFile << """
            #define FUNC(X, Y) X
            #define HEADER_(X, Y) X ## Y
            #define HEADER(X, Y) HEADER_(X, Y)
            #define PREFIX FUNC
            #define SUFFIX ("hello.h", ignore)
            #include HEADER(PREFIX, SUFFIX) // replaced with HEADER_("hello.h", ignore) then FUNC("hello.h", ignore)
        """

        expect:
        resolve() == [header]
    }

    def "can produce a macro function call by concatenating name and args that contain token concatenation"() {
        given:
        sourceFile << """
            #define HEADER_3 "hello.h"
            #define FUNC(X, Y) X
            #define CONCAT_(X, Y) X ## Y
            #define CONCAT(X, Y) CONCAT_(X, Y)
            #define ARGS (HEADER_ ## 3, ~)
            #include CONCAT(FUNC, ARGS)
        """

        expect:
        resolve() == [header]
    }

    def "can produce a macro function call by nesting token concatenations"() {
        given:
        sourceFile << """
            #define ARGS_3 ("hello.h")
            #define FUNC_NAME() F
            #define F(X) X
            #define CONCAT_(X, Y) X ## Y
            #define CONCAT(X, Y) CONCAT_(X, Y)
            #define HEADER(X, Y, Z) CONCAT(X ## Y, ARGS_## Z)
            #include HEADER(FUNC_NAME, (), 3)
        """

        expect:
        resolve() == [header]
    }

    def "can produce a macro function call by concatenating name and wrapping args in parens"() {
        given:
        sourceFile << """
            #define FUNC(X, Y) X
            #define HEADER_(X, Y) X ## Y
            #define HEADER(X, Y, Z) HEADER_(X, (Y, Z))
            #define PREFIX FUNC
            #include HEADER(PREFIX, "hello.h", ignore) // replaced with HEADER_(FUNC, "hello.h", ignore) then FUNC("hello.h", ignore)
        """

        expect:
        resolve() == [header]
    }

    def "resolves token concatenation multiple times for different values of left and right hand sides"() {
        given:
        def header1 = includeDir.createFile("hello1.h")
        def header2 = includeDir.createFile("hello2.h")
        def header3 = includeDir.createFile("hello3.h")
        def header4 = includeDir.createFile("hello4.h")
        sourceFile << """
            #define HEADER1_NAME1 "hello1.h"
            #define HEADER1_NAME2 "hello2.h"
            #define HEADER2_NAME1 "hello3.h"
            #define HEADER2_NAME2 "hello4.h"
            #if 0
            #define HEADER HEADER1_
            #define NAME NAME1
            #else
            #define HEADER HEADER2_
            #define NAME NAME2
            #endif
            #define HEADER2(X, Y) X ## Y
            #define HEADER(X, Y) HEADER2(X, Y)
            #define PREFIX HEADER
            #define SUFFIX NAME
            #include HEADER(PREFIX, SUFFIX)
        """

        expect:
        resolve() == [header1, header2, header3, header4]
    }

    def "can produce a macro function call by returning function arg that is sequence of expressions"() {
        given:
        sourceFile << """
            #define FUNC2(X) X
            #define FUNC1(X) FUNC2
            #define HEADER(X) X
            #include HEADER(FUNC1 (~) ("hello.h")) // replaced by FUNC1(~)("hello.h") then FUNC2("hello.h")
        """

        expect:
        resolve() == [header]
    }

    def "can produce a macro function call by returning function arg that is sequence of macros that expand to functions"() {
        given:
        sourceFile << """
            #define FUNC1(X) X
            #define FUNC2(X) (X)
            #define ARGS ("hello.h")

            #define HEADER_(X) X
            #define HEADER(X) HEADER_(FUNC1 FUNC2 X)

            #include HEADER(ARGS) // replaced by FUNC1 FUNC2 ("hello.h") then FUNC1 ("hello.h")
        """

        expect:
        resolve() == [header]
    }

    def resolve() {
        def directives = parser.parseSource(sourceFile)
        directives = serialize(directives, serializer)
        def macros = new CollectingMacroLookup()
        macros.append(sourceFile, directives)
        def result = resolver.resolveInclude(sourceFile, directives.all.first(), macros)
        assert result.complete
        result.files.file as List
    }

    void doesNotResolve(String reportedAs) {
        def directives = parser.parseSource(sourceFile)
        directives = serialize(directives, serializer)
        def macros = new CollectingMacroLookup()
        macros.append(sourceFile, directives)
        def result = resolver.resolveInclude(sourceFile, directives.all.first(), macros)
        assert directives.all.first().asSourceText == reportedAs
        assert !result.complete
        assert result.files.empty
    }
}
