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
        doesNotResolve()
    }

    def "does not resolve unknown macro"() {
        given:
        sourceFile << """
            #include HEADER
        """

        expect:
        doesNotResolve()
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
        doesNotResolve()
    }

    def "does not resolve macro function call with too many args"() {
        given:
        sourceFile << """
            #define HEADER() "hello.h"
            #include HEADER(X)
        """

        expect:
        doesNotResolve()
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

    def resolve() {
        def directives = parser.parseSource(sourceFile)
        directives = serialize(directives, serializer)
        def result = resolver.resolveInclude(sourceFile, directives.all.first(), [directives])
        assert result.complete
        result.files
    }

    void doesNotResolve() {
        def directives = parser.parseSource(sourceFile)
        directives = serialize(directives, serializer)
        def result = resolver.resolveInclude(sourceFile, directives.all.first(), [directives])
        assert !result.complete
        assert result.files.empty
    }
}
