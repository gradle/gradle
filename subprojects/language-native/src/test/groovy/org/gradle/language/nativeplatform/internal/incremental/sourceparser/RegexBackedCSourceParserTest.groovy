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
package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import com.google.common.collect.ImmutableList
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.IncludeType
import org.gradle.language.nativeplatform.internal.Macro
import org.gradle.language.nativeplatform.internal.MacroFunction
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class RegexBackedCSourceParserTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    CSourceParser parser = new RegexBackedCSourceParser()

    protected TestFile getSourceFile() {
        testDirectory.file('source.c')
    }

    TestFile getTestDirectory() {
        temporaryFolder.testDirectory
    }

    IncludeDirectives getParsedSource() {
        parser.parseSource(sourceFile)
    }

    List<Include> getIncludes() {
        return parsedSource.includesOnly
    }

    List<Include> getImports() {
        return parsedSource.all - parsedSource.includesOnly
    }

    List<String> getFound() {
        return parsedSource.all.collect { it.value }
    }

    def noIncludes() {
        assert includes == []
        true
    }

    def noImports() {
        assert imports == []
        true
    }

    def useDirective(String directive) {
        sourceFile.text = sourceFile.text.replace("include", directive)
    }

    Expression expression(String value) {
        return RegexBackedCSourceParser.parseExpression(value)
    }

    Include include(String value, boolean isImport = false) {
        def expression = RegexBackedCSourceParser.parseExpression(value)
        return DefaultInclude.create(expression, isImport)
    }

    Macro macro(String name, String value) {
        def expression = RegexBackedCSourceParser.parseExpression(value)
        return new DefaultMacro(name, expression.type, expression.value)
    }

    MacroFunction macroFunction(String name, int parameters = 0, String value) {
        def expression = RegexBackedCSourceParser.parseExpression(value)
        return new ReturnFixedValueMacroFunction(name, parameters, expression.type, expression.value)
    }

    Macro unresolvedMacro(String name) {
        return new UnresolveableMacro(name)
    }

    MacroFunction unresolvedMacroFunction(String name, int parameters = 0) {
        return new UnresolveableMacroFunction(name, parameters)
    }

    List<Macro> getMacros() {
        return parsedSource.macros
    }

    List<MacroFunction> getMacroFunctions() {
        return parsedSource.macrosFunctions
    }

    def "parses file with no includes"() {
        when:
        sourceFile << ""

        then:
        noIncludes()
        noImports()
    }

    def "finds quoted include"() {
        when:
        sourceFile << """
    #include "test.h"
"""

        then:
        includes == [new DefaultInclude('test.h', false, IncludeType.QUOTED)]

        and:
        noImports()
    }

    def "finds quoted include on first line of file"() {
        when:
        sourceFile << '#include "test.h"'

        then:
        includes == ['"test.h"'].collect { include(it) }

        and:
        noImports()
    }

    def "finds system include"() {
        when:
        sourceFile << """
    #include <test.h>
"""

        then:
        includes == [new DefaultInclude('test.h', false, IncludeType.SYSTEM)]

        and:
        noImports()
    }

    def "finds system include on first line of file"() {
        when:
        sourceFile << '#include <test.h>'

        then:
        includes == ['<test.h>'].collect { include(it) }

        and:
        noImports()
    }

    def "finds macro include"() {
        when:
        sourceFile << """
    #include ${include}
"""

        then:
        includes == [new DefaultInclude(include, false, IncludeType.MACRO)]

        and:
        noImports()

        where:
        include << ['A', 'DEFINED', '_A$2', 'mixedDefined', '__DEFINED__']
    }

    def "finds macro function include"() {
        when:
        sourceFile << """
    #include ${include}
"""

        then:
        includes == [new DefaultInclude(macro, false, IncludeType.MACRO_FUNCTION)]

        and:
        noImports()

        where:
        include       | macro
        'A()'         | 'A'
        'A( )'        | 'A'
        'ABC( )'      | 'ABC'
        '_A$2( )'     | '_A$2'
        'abc( )'      | 'abc'
        'a12  \t(\t)' | 'a12'
    }

    def "finds macro function include with parameters"() {
        when:
        sourceFile << """
    #include ${include}
"""

        then:
        includes == [new MacroFunctionInclude(macro, false, ImmutableList.copyOf(parameters.collect { expression(it) }))]

        and:
        noImports()

        where:
        include               | macro | parameters
        'A(X)'                | 'A'   | ['X']
        'A( X )'              | 'A'   | ['X']
        'ABC(x,y)'            | 'ABC' | ['x', 'y']
        'ABC( \t x \t,  y  )' | 'ABC' | ['x', 'y']
    }

    def "finds other includes"() {
        when:
        sourceFile << """
    #include ${include}
"""

        then:
        includes == [new DefaultInclude(include, false, IncludeType.OTHER)]

        and:
        noImports()

        where:
        include << ['DEFINED(<abc.h>)', 'not an include', 'BROKEN(', '@(X', '"abc.h" DEFINED']
    }

    def "finds multiple includes"() {
        when:
        sourceFile << """
    #include "test1"
    #include "test2"
    #include <system1>
    #include <system2>
    #include DEFINED
    #include DEFINED()
    #include DEFINED(ABC)
    #include DEFINED(X, Y)
    #include not an include
"""
        then:
        includes == ['"test1"', '"test2"', '<system1>', '<system2>', 'DEFINED', 'DEFINED()', 'DEFINED(ABC)', 'DEFINED(X, Y)', 'not an include'].collect { include(it) }

        and:
        noImports()
    }

    def "finds quoted import"() {
        when:
        sourceFile << """
            #import "test.h"
        """

        then:
        imports == ['"test.h"'].collect { include(it, true) }

        and:
        noIncludes()
    }

    def "finds system import"() {
        when:
        sourceFile << """
            #import <test.h>
        """

        then:
        imports == ['<test.h>'].collect { include(it, true) }

        and:
        noIncludes()
    }

    def "finds defined import"() {
        when:
        sourceFile << """
            #import DEFINED
        """

        then:
        imports == ['DEFINED'].collect { include(it, true) }

        and:
        noIncludes()
    }

    def "finds multiple imports"() {
        when:
        sourceFile << """
    #import "test1"
    #import "test2"
    #import <system1>
    #import <system2>
    #import DEFINED
"""
        then:
        imports == ['"test1"', '"test2"', '<system1>', '<system2>', 'DEFINED'].collect { include(it, true) }

        and:
        noIncludes()
    }

    def "finds mixed import include statement imports"() {
        when:
        sourceFile << """
    #import "test1"
    #include "test2"
    #import "test3"
    #include "test4"
    #import <system1>
    #import <system2>
    #include <system3>
    #import <system4>
    #include DEFINED1
    #import DEFINED2
"""
        then:
        includes == ['"test2"', '"test4"', '<system3>', 'DEFINED1'].collect { include(it) }
        imports == ['"test1"', '"test3"', '<system1>', '<system2>', '<system4>', 'DEFINED2'].collect { include(it, true) }
    }

    def "preserves order of all includes and imports"() {
        when:
        sourceFile << """
    #import "test1"
    #include "test2"
    #import "test3"
    #include "test4"
    #import <system1>
    #import <system2>
    #include <system3>
    #import <system4>
    #include DEFINED1
    #import DEFINED2
"""
        then:
        found == ['test1', 'test2', 'test3', 'test4', 'system1', 'system2', 'system3', 'system4', 'DEFINED1', 'DEFINED2']
    }

    @Unroll
    def "finds #directive surrounded by different whitespace"() {
        when:
        sourceFile << """
#include     "test1"
#include\t"test2"\t
\t#include\t"test3"
#include"test4"

#include     <system1>
#include\t<system2>\t
\t#include\t<system3>
#include<system4>
"""
        and:
        useDirective(directive)

        then:
        found == ['test1', 'test2', 'test3', 'test4',
                  'system1', 'system2', 'system3', 'system4']

        where:
        directive << ["include", "import"]
    }

    @Unroll
    def "finds #directive where whitespace surrounds the # character"() {
        when:
        sourceFile << """
  #  include   "test1"
\t#\tinclude "test2"
\u0000#include "test3"

  #  include   <system1>
\t#\tinclude <system2>
\u0000#include <system3>
"""
        and:
        useDirective(directive)

        then:
        found == ['test1', 'test2', 'test3', 'system1', 'system2', 'system3']

        where:
        directive << ["include", "import"]
    }

    def "ignores comment after directive"() {
        when:
        sourceFile << """
#include "test1"  // A comment here
#include "test2" /* A comment here */
#include "test3" /*
   A comment here
*/
#include <system1>  // A comment here
#include <system2> /* A comment here */
#include <system3> /*
   A comment here
*/
#include MACRO1  // A comment here 
#include MACRO2 /*
   A comment here
*/
#include MACRO1()  // A comment here 
#include MACRO2() /*
   A comment here
*/
"""
        then:
        includes == ['"test1"', '"test2"', '"test3"', '<system1>', '<system2>', '<system3>', 'MACRO1', 'MACRO2', 'MACRO1()', 'MACRO2()'].collect { include(it) }
    }

    @Unroll
    def "finds #directive where comment in place of whitespace"() {
        when:
        sourceFile << """
#include/* a comment here*/"test1"
#/* a
    comment
    here*/include "test2"
/* a comment here*/#include/* a comment here*/"test3"
#include/* a comment here*/<system1>
#/* a comment here*/include <system2>
/* a comment here*/#include/* a comment here*/DEFINED
"""
        useDirective(directive)

        then:
        found == ['test1', 'test2', 'test3', 'system1', 'system2', 'DEFINED']

        where:
        directive << ["include", "import"]
    }

    @Unroll
    def "finds #directive with no whitespace"() {
        when:
        sourceFile << """
#include"test1"
#include<test2>
"""
        useDirective(directive)

        then:
        found == ['test1', 'test2']

        where:
        directive << ["include", "import"]
    }

    def "find quoted include with special characters"() {
        when:
        sourceFile << """
    #include "$included"
    #import "$included"
"""
        then:
        includes == ['"' + included + '"'].collect { include(it) }
        imports == ['"' + included + '"'].collect { include(it, true) }

        where:
        included << ["test'file", "testfile'", "'testfile'", "test<>file", "test>file", "<testFile>", "test<file", "test file"]
    }

    def "find system include with special characters"() {
        when:
        sourceFile << """
    #include <$included>
    #import <$included>
"""
        then:
        includes == ['<' + included + '>'].collect { include(it) }
        imports == ['<' + included + '>'].collect { include(it, true) }

        where:
        included << ["test'file", "testfile'", "'testfile'", "test<file", "test\"file", "\"testFile\"", "test file"]
    }

    @Unroll
    def "ignores #directive inside a quoted string"() {
        when:
        sourceFile << """
    printf("use #include <stdio.h>");
    printf("use #include \\"test1\\");
"""
        and:
        useDirective(directive)

        then:
        noIncludes()
        noImports()
        macros.empty

        where:
        directive << ["include", "import", "define"]
    }

    @Unroll
    def "ignores #directive that is commented out"() {
        when:
        sourceFile << """
/*
    #include "test1"
    #include <system1>
*/
/* #include "test2" */
/* #include <system3> */

//    #include "test3"
//    #include <system3>
"""
        and:
        useDirective(directive)

        then:
        noIncludes()
        noImports()
        macros.empty

        where:
        directive << ["include", "import", "define"]
    }

    def "considers anything that starts with include or import directive and does not have an empty value as an include"() {
        when:
        sourceFile << """
include
#
# include
# include    // only white space
# import
# import   /*
*/

void # include <thing>

#import <

# inklude <thing.h>
# included
# included "thing.h"
# include_other
# include_other <thing.h>

#import thing.h
#import thing.h"
#import "thing.h>
#include <thing.h
#include "thing.h

#include
<thing.h>

#include 'thing.h' extra stuff

 # include X(
 # include X( ++
 # include X(a,

"""

        then:
        includes.size() == 6
        imports.size() == 4
    }

    def "detects imports with line=continuation"() {
        when:
        sourceFile << """
#include \\
"test1"
#\\
include\\
 "test2"
#incl\\
ude "te\\
st3"
"""

        then:
        includes == ['"test1"', '"test2"', '"test3"'].collect { include(it) }
    }

    def "finds object-like macro directive whose value is a string constant"() {
        when:
        sourceFile << """
#define SOME_STRING "abc"
"""

        then:
        macros == [macro('SOME_STRING', '"abc"')]
        macroFunctions.empty
    }

    def "finds object-like macro directive whose value is a macro reference"() {
        when:
        sourceFile << """
#define SOME_STRING ${value}
"""

        then:
        macros == [macro('SOME_STRING', value)]
        macroFunctions.empty

        where:
        value << ['a', '_a_123_', 'a$b']
    }

    def "finds object-like macro directive whose value is not a string constant or macro reference"() {
        when:
        sourceFile << """
#define SOME_STRING ${value}
"""

        then:
        macros == [unresolvedMacro('SOME_STRING')]
        macroFunctions.empty

        where:
        value << ["one two three", "a++", "one(<abc.h>)", "-12", "(X) #X"]
    }

    def "handles various separators in an object-like macro directive"() {
        when:
        sourceFile << """
  #   define     SOME_STRING         "abc"      // some extra
  /*    
  
  */  \\
  #/*
  
  
  */\u0000define \\
        /*
         */STRING_2\\
/*         
*/"123"\\
    /* */   // some extra"""

        then:
        macros == [macro('SOME_STRING', 'abc'), macro('STRING_2', '123')]
        macroFunctions.empty
    }

    def "finds object-like macro directive with no whitespace"() {
        when:
        sourceFile << """
#define A"abc"
#define B<abc>
"""

        then:
        macros == [macro('A', '"abc"'), macro('B', '<abc>')]
        macroFunctions.empty
    }

    def "finds object-like macro directive with no body"() {
        when:
        sourceFile << """
#define SOME_STRING
"""

        then:
        macros == [unresolvedMacro('SOME_STRING')]
        macroFunctions.empty
    }

    def "finds object-like macro directive with empty body"() {
        when:
        sourceFile << """
#define SOME_STRING    // ignore
"""

        then:
        macros == [unresolvedMacro('SOME_STRING')]
        macroFunctions.empty
    }

    def "finds multiple object-like macro directives"() {
        when:
        sourceFile << """
#ifdef THING
#define SOME_STRING "abc"
#else
#define SOME_STRING "xyz"
#endif
#define OTHER "1234"
#define EMPTY
#define FUNCTION abc(123)
#define UNKNOWN abc 123
"""

        then:
        macros == [
            macro('SOME_STRING', '"abc"'),
            macro('SOME_STRING', '"xyz"'),
            macro('OTHER', '"1234"'),
            unresolvedMacro('EMPTY'),
            macro('FUNCTION', 'abc(123)'),
            unresolvedMacro('UNKNOWN'),
        ]
        macroFunctions.empty
    }

    def "finds function-like macro directive with no parameters whose body is a string constant"() {
        when:
        sourceFile << """
#define A() "abc"
"""

        then:
        macros.empty
        macroFunctions == [macroFunction('A', '"abc"')]
    }

    def "finds function-like macro directive with no parameters whose body is a system path"() {
        when:
        sourceFile << """
#define A() <abc.h>
"""

        then:
        macros.empty
        macroFunctions == [macroFunction('A', '<abc.h>')]
    }

    def "finds function-like macro directive with no parameters whose body is a macro"() {
        when:
        sourceFile << """
#define A() ABC_H
"""

        then:
        macros.empty
        macroFunctions == [macroFunction('A', 'ABC_H')]
    }

    def "finds function-like macro directive with no parameters whose body is some other value"() {
        when:
        sourceFile << """
#define A() ${definition}
"""

        then:
        macros.empty
        macroFunctions == [unresolvedMacroFunction('A', 0)]

        where:
        definition << ['@', 'A(abc)', '"a12" 12 + 4']
    }

    def "finds function-like macro directive with no parameters whose body is empty"() {
        when:
        sourceFile << """
#define A()
"""

        then:
        macros.empty
        macroFunctions == [unresolvedMacroFunction('A')]
    }

    def "finds function-like macro directive with multiple parameters whose body is a string constant"() {
        when:
        sourceFile << """
#define ${definition} "abc"
"""

        then:
        macros.empty
        macroFunctions == [macroFunction(macro, parameters, '"abc"')]

        where:
        definition   | macro | parameters
        'A(X)'       | 'A'   | 1
        'ABC(X,Y)'   | 'ABC' | 2
        '_a$(X,Y,Z)' | '_a$' | 3
    }

    def "finds function-like macro directive with multiple parameters whose body is a parameter"() {
        when:
        sourceFile << """
#define ${definition} ${body}
"""

        then:
        macros.empty
        macroFunctions == [new ReturnParameterMacroFunction(macro, parameters, paramToReturn)]

        where:
        definition         | body  | macro | parameters | paramToReturn
        'A(X)'             | 'X'   | 'A'   | 1          | 0
        'ABC(Y, X)'        | 'X'   | 'ABC' | 2          | 1
        '_a$(a1, b2, _a$)' | '_a$' | '_a$' | 3          | 2
    }

    def "finds function-like macro directive with multiple parameters whose body is a macro"() {
        when:
        sourceFile << """
#define A(X, Y) ${body}
"""

        then:
        macros.empty
        macroFunctions == [macroFunction('A', 2, body)]

        where:
        body << ['_ABC_', '_a$', 'A1']
    }

    def "finds function-like macro directive with multiple parameters whose body is some other value"() {
        when:
        sourceFile << """
#define A(X, Y) ${body}
"""

        then:
        macros.empty
        macroFunctions == [unresolvedMacroFunction('A', 2)]

        where:
        body << ['@', 'Defined(a.h)', 'A(B(C, D()))', '"abc" 12 + 5']
    }

    def "finds function-like macro directive with multiple parameters whose body is empty"() {
        when:
        sourceFile << """
#define ${definition}
"""

        then:
        macros.empty
        macroFunctions == [unresolvedMacroFunction(macro, parameters)]

        where:
        definition   | macro | parameters
        'A(X)'       | 'A'   | 1
        'ABC(X,Y)'   | 'ABC' | 2
        '_a$(X,Y,Z)' | '_a$' | 3
    }

    def "handles whitespace in function-like macro definition"() {
        when:
        sourceFile << """
#define ${definition} "abc"
"""

        then:
        macros.empty
        macroFunctions == [macroFunction(macro, parameters, '"abc"')]

        where:
        definition             | macro | parameters
        'A(  \t)'              | 'A'   | 0
        'B( X  \t)'            | 'B'   | 1
        'ABC( X  \t,  \t Y  )' | 'ABC' | 2
    }

    def "handles various separators in an function-like macro directive"() {
        when:
        sourceFile << """
  #   define     SOME_STRING(     )     "abc"      // some extra
  /*    
  
  */  \\
  #/*
  
  
  */ define \\
        /*
         */STRING_2(\\
/*         
*/)/* */"123"\\
    /* */  "some extra"""

        then:
        macros.empty
        macroFunctions == [
            macroFunction('SOME_STRING', '"abc"'),
            unresolvedMacroFunction('STRING_2')
        ]
    }

    def "ignores badly formed define directives"() {
        when:
        sourceFile << """
#define
#define  // white space
#define ()
#define ( _
#define ( _ )
#define X(
#define X(abc
#define X( ,
#define X( abc,
#define X( abc, ,
# define @(Y) Z
"""

        then:
        macros.empty
        macroFunctions.empty
    }
}
