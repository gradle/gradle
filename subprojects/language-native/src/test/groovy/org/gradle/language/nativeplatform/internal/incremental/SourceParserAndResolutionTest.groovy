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

import groovy.transform.NotYetImplemented
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.IncludeDirectivesSerializer
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.RegexBackedCSourceParser
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

/**
 * An integration test that covers source parsing and include resolution plus persistence of parsed state.
 */
class SourceParserAndResolutionTest extends SerializerSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def includeDir = tmpDir.createDir("headers")
    def header = includeDir.createFile("hello.h")
    def sourceDir = tmpDir.createDir("src")
    def sourceFile = sourceDir.createFile("src.cpp")
    def resolver = new DefaultSourceIncludesResolver([includeDir])
    def parser = new RegexBackedCSourceParser()
    def serializer = new IncludeDirectivesSerializer()

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

    def "resolves macro with value that is a macro"() {
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

    def "resolves macro with value that is token concatenation"() {
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

    def "resolves macro with multiple values"() {
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

    def "does not resolve macro with multiple tokens"() {
        given:
        sourceFile << """
            #define HEADER 12 + 6
            #include HEADER
        """

        expect:
        doesNotResolve('#include HEADER')
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
            #define HEADER() "hello.h"
            #define HEADER(X) X
            #include HEADER(HEADER())
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

    def "resolves macro function with arg that returns macro function call with arg that is parameter"() {
        given:
        sourceFile << """
            #define HEADER_(X, Y) X
            #define HEADER(X) HEADER_(X, ignore)
            #include HEADER("hello.h")
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

    @NotYetImplemented
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

    @NotYetImplemented
    def "resolves macro function with multiple args that returns result of function that concatenates the args"() {
        given:
        sourceFile << """
            #define HEADER_NAME "hello.h"
            #define HEADER_ broken // should not be referenced
            #define NAME broken // should not be referenced
            #define HEADER_(X, Y) X ## Y
            #define HEADER(X, Y) HEADER_(X, Y)
            #define PREFIX HEADER_
            #define SUFFIX NAME
            #include HEADER(PREFIX, SUFFIX) // replaced with HEADER_(HEADER_, NAME) then HEADER_NAME then "hello.h"
        """

        expect:
        resolve() == [header]
    }

    def resolve() {
        def directives = parser.parseSource(sourceFile)
        directives = serialize(directives, serializer)
        def macros = new MacroLookup()
        macros.append(sourceFile, directives)
        def result = resolver.resolveInclude(sourceFile, directives.all.first(), macros)
        assert result.complete
        result.files
    }

    void doesNotResolve(String reportedAs) {
        def directives = parser.parseSource(sourceFile)
        directives = serialize(directives, serializer)
        def macros = new MacroLookup()
        macros.append(sourceFile, directives)
        def result = resolver.resolveInclude(sourceFile, directives.all.first(), macros)
        assert directives.all.first().asSourceText == reportedAs
        assert !result.complete
        assert result.files.empty
    }
}
