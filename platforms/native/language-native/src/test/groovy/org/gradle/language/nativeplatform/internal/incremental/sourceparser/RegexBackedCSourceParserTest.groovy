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
import com.google.common.collect.Lists
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

class RegexBackedCSourceParserTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
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

    Expression token(String value) {
        return new SimpleExpression(value, value.matches('(\\w|_|\\$)(\\w|\\d|_|\\$)*') ? IncludeType.IDENTIFIER : IncludeType.TOKEN)
    }

    Expression tokens(String value) {
        if (value.empty) {
            return new SimpleExpression(null, IncludeType.EXPRESSIONS)
        }
        def e = RegexBackedCSourceParser.parseExpression("m($value)")
        assert e.arguments.size() == 1 && (e.arguments[0].type == IncludeType.EXPRESSIONS || e.arguments[0].type == IncludeType.ARGS_LIST || e.arguments[0].type == IncludeType.TOKEN_CONCATENATION)
        return e.arguments[0]
    }

    Include include(String value, boolean isImport = false) {
        def expression = RegexBackedCSourceParser.parseExpression(value)
        return IncludeWithSimpleExpression.create(expression, isImport)
    }

    Macro macro(String name, String value) {
        def expression = RegexBackedCSourceParser.parseExpression(value)
        if (!expression.arguments.empty) {
            return new MacroWithComplexExpression(name, expression.type, expression.value, expression.arguments)
        }
        return new MacroWithSimpleExpression(name, expression.type, expression.value)
    }

    Macro macro(String name, IncludeType type, String value, List<?> args) {
        if (!args.empty) {
            return new MacroWithComplexExpression(name, type, value, args.collect { it instanceof Expression ? it : expression(it as String) })
        }
        return new MacroWithSimpleExpression(name, type, value)
    }

    MacroFunction macroFunction(String name, int parameters = 0, String value) {
        def expression = RegexBackedCSourceParser.parseExpression(value)
        return new ReturnFixedValueMacroFunction(name, parameters, expression.type, expression.value, expression.arguments)
    }

    MacroFunction macroFunction(String name, int parameters = 0, IncludeType type, String value, List<?> args) {
        return new ReturnFixedValueMacroFunction(name, parameters, type, value, args.collect { it instanceof Expression ? it : expression(it as String) })
    }

    Macro unresolvedMacro(String name) {
        return new UnresolvableMacro(name)
    }

    MacroFunction unresolvedMacroFunction(String name, int parameters = 0) {
        return new UnresolvableMacroFunction(name, parameters)
    }

    List<Macro> getMacros() {
        return Lists.newArrayList(parsedSource.allMacros)
    }

    List<MacroFunction> getMacroFunctions() {
        return Lists.newArrayList(parsedSource.allMacroFunctions)
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
        includes == [new IncludeWithSimpleExpression('test.h', false, IncludeType.QUOTED)]

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
        includes == [new IncludeWithSimpleExpression('test.h', false, IncludeType.SYSTEM)]

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
        includes == [new IncludeWithSimpleExpression(include, false, IncludeType.MACRO)]

        and:
        noImports()

        where:
        include << ['A', 'DEFINED', '_A$2', 'mixedDefined', '__DEFINED__']
    }

    def "finds macro function call include"() {
        when:
        sourceFile << """
    #include ${include}
"""

        then:
        includes == [new IncludeWithSimpleExpression(macro, false, IncludeType.MACRO_FUNCTION)]

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

    def "finds macro function call include with parameters"() {
        when:
        sourceFile << """
    #include ${include}
"""

        then:
        includes == [new IncludeWithMacroFunctionCallExpression(macro, false, ImmutableList.copyOf(parameters))]

        and:
        noImports()

        where:
        include                                            | macro   | parameters
        'A(X)'                                             | 'A'     | [token('X')]
        'A( X )'                                           | 'A'     | [token('X')]
        'ABC(x,y)'                                         | 'ABC'   | [token('x'), token('y')]
        'ABC( \t x \t,  y  )'                              | 'ABC'   | [token('x'), token('y')]
        'A ( "include.h" )'                                | 'A'     | [expression('"include.h"')]
        'A ( <include.h> )'                                | 'A'     | [expression('<include.h>')]
        'A ( _b )'                                         | 'A'     | [token('_b')]
        'A ( _b(c) )'                                      | 'A'     | [expression('_b(c)')]
        ' \tA ( "a.h", <b.h>, b$(a,b,c("b.h")  ), \tZ \t)' | 'A'     | [expression('"a.h"'), expression('<b.h>'), expression('b$(a,b,c("b.h"))'), token('Z')]
        'a( a b c )'                                       | 'a'     | [tokens('a b c')]
        'a( a b, c d + f )'                                | 'a'     | [tokens('a b'), tokens('c d + f')]
        '_func(1+2)'                                       | '_func' | [tokens('1+2')]
        'a(A, B(1+2))'                                     | 'a'     | [token('A'), expression('B(1+2)')]
        'a( ( x ), ( a )\t )'                              | 'a'     | [tokens('(x)'), tokens('(a)')]
        'a(,)'                                             | 'a'     | [tokens(''), tokens('')]
        'a((a,b))'                                         | 'a'     | [tokens('(a,b)')]
        'a((a,b,(c, d)))'                                  | 'a'     | [tokens('(a,b,(c,d))')]
        'a( ( a ,,, b ), c)'                               | 'a'     | [tokens('(a,,,b)'), token('c')]
        'a(~, ~)'                                          | 'a'     | [token('~'), token('~')]
        'a(~ ? ~)'                                         | 'a'     | [tokens('~?~')]
    }

    def "finds other includes that cannot be resolved"() {
        when:
        sourceFile << """
    #include ${include}
"""

        then:
        includes == [new IncludeWithSimpleExpression(include, false, IncludeType.OTHER)]

        and:
        noImports()

        where:
        include << [
            'not an include',
            'BROKEN(',
            'broken(A,',
            'broken(a, b',
            'broken(a, ((b, c)',
            '@(X',
            '"abc.h" DEFINED',
            'A##B',
            'A#B',
            'A##.',
            'A##',
            'a(()',
            'a(()))',
        ]
    }

    def "finds multiple includes"() {
        when:
        sourceFile << """
    #include "test1"
    #include "test2"
    #include <system1>
    #include <system2>
    #include DEFINED
    #include DEFINED2
    #include DEFINED()
    #include DEFINED2()
    #include DEFINED(ABC)
    #include DEFINED(X)
    #include DEFINED(X, Y)
    #include DEFINED(A, Y)
    #include not an include
"""
        then:
        includes == [
            '"test1"',
            '"test2"',
            '<system1>',
            '<system2>',
            'DEFINED',
            'DEFINED2',
            'DEFINED()',
            'DEFINED2()',
            'DEFINED(ABC)',
            'DEFINED(X)',
            'DEFINED(X, Y)',
            'DEFINED(A, Y)',
            'not an include'
        ].collect { include(it) }

        and:
        noImports()
    }

    def "discards duplicate includes"() {
        when:
        sourceFile << """
    #include "test1"
    #include "test1"
    #include "test2"
    #include <test1>
    #include <test1>
    #include <test2>
    #include DEFINED
    #include DEFINED
    #include DEFINED()
    #include DEFINED()
    #include DEFINED(ABC)
    #include DEFINED(ABC)
    #include DEFINED(X)
    #include DEFINED(X, Y)
    #include DEFINED(X, Y)
    #include not an include
    #include not an include 2
    #include not an include
"""
        then:
        includes == [
            '"test1"',
            '"test2"',
            '<test1>',
            '<test2>',
            'DEFINED',
            'DEFINED()',
            'DEFINED(ABC)',
            'DEFINED(X)',
            'DEFINED(X, Y)',
            'not an include',
            'not an include 2'
        ].collect { include(it) }

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

    def "finds object-like macro directive whose value is a macro function call"() {
        when:
        sourceFile << """
#define SOME_STRING ${value}
"""

        then:
        macros == [macro('SOME_STRING', IncludeType.MACRO_FUNCTION, function, args)]
        macroFunctions.empty

        where:
        value                  | function  | args
        'a()'                  | 'a'       | []
        '_a_123_(_a1, $2)'     | '_a_123_' | [token('_a1'), token('$2')]
        'a$b(X,Y)'             | 'a$b'     | [token('X'), token('Y')]
        ' A( X, Y(Z)  )'       | 'A'       | [token('X'), expression('Y(Z)')]
        ' A( (  X ) , ( y  ))' | 'A'       | [tokens('(X)'), tokens('(y)')]
        ' A((a, b, c))'        | 'A'       | [tokens('(a, b, c)')]
        ' A(())'               | 'A'       | [tokens('()')]
        ' A((a, b), c)'        | 'A'       | [tokens('(a, b)'), token('c')]
        ' A((,))'              | 'A'       | [tokens('(,)')]
        ' A((,,,), c)'         | 'A'       | [tokens('(,,,)'), token('c')]
        '_a ( p1 p2 p3 )'      | '_a'      | [tokens('p1 p2 p3')]
        '_a ( 1, (2+3) )'      | '_a'      | [token('1'), tokens('(2+3)')]
        '_a((, ) a b c)'       | '_a'      | [tokens('(,) a b c')]
        'func( (a) ( b ))'     | 'func'    | [tokens('(a)(b)')]
    }

    def "finds object-like macro directive whose value is token concatenation"() {
        when:
        sourceFile << """
#define SOME_STRING ${value}
"""

        then:
        macros == [macro('SOME_STRING', IncludeType.EXPAND_TOKEN_CONCATENATION, null, [token(left), token(right)])]
        macroFunctions.empty

        where:
        value           | left  | right
        'A##B'          | 'A'   | 'B'
        ' \tA  ##\tB  ' | 'A'   | 'B'
        '_a$##h2_'      | '_a$' | 'h2_'
    }

    def "finds object-like macro directive whose value is argument list"() {
        when:
        sourceFile << """
#define SOME_STRING ${value}
"""

        then:
        macros == [macro('SOME_STRING', IncludeType.ARGS_LIST, null, params)]
        macroFunctions.empty

        where:
        value              | params
        '()'               | []
        '( a )'            | [expression('a')]
        '( "a.h" )'        | [expression('"a.h"')]
        '( <a.h> )'        | [expression('<a.h>')]
        '( , )'            | [tokens(''), tokens('')]
        '( ~ )'            | [token('~')]
        '( ~, , ? )'       | [token('~'), tokens(''), token('?')]
        '(a, b, c)'        | [expression('a'), expression('b'), expression('c')]
        '(1, 2, 3)'        | [expression('1'), expression('2'), expression('3')]
        '(<a.h>, "b.h")'   | [expression('<a.h>'), expression('"b.h"')]
        '( a + b )'        | [tokens('a + b')]
        '(a, (c, (d), e))' | [expression('a'), expression('(c, (d), e)')]
        '( x ## y )'       | [expression('x ## y')]
    }

    def "finds object-like macro directive whose body is a token"() {
        when:
        sourceFile << """
#define SOME_STRING ${value}
"""

        then:
        macros == [macro('SOME_STRING', IncludeType.TOKEN, value, [])]

        where:
        value << ['~', '@']
    }

    def "finds object-like macro directive whose value cannot be resolved"() {
        when:
        sourceFile << """
#define SOME_STRING ${value}
"""

        then:
        macros == [unresolvedMacro('SOME_STRING')]
        macroFunctions.empty

        where:
        value << [
            "one two three",
            "a++",
            "-12",
            "(X) #X",
            "a(b) c(d)",
            "A(12)##@",
            "A##",
            'A##@',
            '##B',
            'x ## y a ## b',
            'a(b()',
            'a(()) more',
            'a(  ,',
            '(',
            '( A',
            '( A ##',
            '( A ## b',
            '() ()',
            '(a) (b)',
            '~ ?'
        ]
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
#define FUNCTION abc(a, b, c)
#define UNKNOWN abc 123
"""

        then:
        macros == [
            macro('SOME_STRING', '"abc"'),
            macro('SOME_STRING', '"xyz"'),
            macro('OTHER', '"1234"'),
            unresolvedMacro('EMPTY'),
            macro('FUNCTION', 'abc(a, b, c)'),
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

    def "finds function-like macro directive with no parameters whose body is a macro function call"() {
        when:
        sourceFile << """
#define A() ${value}
"""

        then:
        macros.empty
        macroFunctions == [macroFunction('A', IncludeType.MACRO_FUNCTION, function, args)]

        where:
        value          | function | args
        'ABC_H(A, Z)'  | 'ABC_H'  | [token('A'), token('Z')]
        'a(1+2)'       | 'a'      | [tokens('1+2')]
        'a ( 1 + 2 ) ' | 'a'      | [tokens('1+2')]
    }

    def "finds function-like macro directive with no parameters whose body is token concatenation"() {
        when:
        sourceFile << """
#define A() ${value}
"""

        then:
        macros.empty
        macroFunctions == [macroFunction('A', IncludeType.EXPAND_TOKEN_CONCATENATION, null, params)]

        where:
        value         | params
        'A ## Z'      | [token('A'), token('Z')]
        'A ## B ## C' | [tokens('A##B'), token('C')]
    }

    def "finds function-like macro directive with no parameters whose body is argument list"() {
        when:
        sourceFile << """
#define SOME_STRING() ${value}
"""

        then:
        macroFunctions == [macroFunction('SOME_STRING', 0, IncludeType.ARGS_LIST, null, params)]

        where:
        value              | params
        '()'               | []
        '( a )'            | [expression('a')]
        '( "a.h" )'        | ['"a.h"']
        '( <a.h> )'        | ['<a.h>']
        '( , )'            | [tokens(''), tokens('')]
        '( ~ )'            | [token('~')]
        '(a, b, c)'        | ['a', 'b', 'c']
        '(1, 2, 3)'        | ['1', '2', '3']
        '(<a.h>, "b.h")'   | ['<a.h>', '"b.h"']
        '(a, (c, (d), e))' | ['a', expression('(c, (d), e)')]
        '(a##b)'           | [expression('a##b')]
        '( a ## b ## c )'  | [expression('a##b##c')]
        '(a b c)'          | [tokens('a b c')]
    }

    def "finds function-like macro directive with no parameters whose body is a token"() {
        when:
        sourceFile << """
#define SOME_STRING() ${value}
"""

        then:
        macroFunctions == [macroFunction('SOME_STRING', 0, IncludeType.TOKEN, value, [])]

        where:
        value << ['~', '@']
    }

    def "finds function-like macro directive with no parameters whose body cannot be resolved"() {
        when:
        sourceFile << """
#define A() ${definition}
"""

        then:
        macros.empty
        macroFunctions == [unresolvedMacroFunction('A', 0)]

        where:
        definition << [
            'A(abc',
            'A(abc , ',
            'a(b) c(d)',
            '"a12" 12 + 4',
            'x##',
            'a##~',
            'a##(b)',
            'a## b ##',
            '( a ## ',
            '(a b',
            '(a b), c',
        ]
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

    def "finds function-like macro directive with no parameters with no whitespace between name and body"() {
        when:
        sourceFile << """
#define A()Y
"""

        then:
        macros.empty
        macroFunctions == [macroFunction('A', 'Y')]
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

    def "finds function-like macro directive with multiple parameters whose body is a macro function call"() {
        when:
        sourceFile << """
#define A(X, Y) ${body}
"""

        then:
        macros.empty
        macroFunctions == [expected]

        where:
        body                          | expected
        "B()"                         | new ReturnFixedValueMacroFunction("A", 2, IncludeType.MACRO_FUNCTION, "B", [])
        "B(Z)"                        | new ReturnFixedValueMacroFunction("A", 2, IncludeType.MACRO_FUNCTION, "B", [token('Z')])
        "B((Z))"                      | new ReturnFixedValueMacroFunction("A", 2, IncludeType.MACRO_FUNCTION, "B", [tokens('(Z)')])
        "B(X, Y)"                     | new ArgsMappingMacroFunction("A", 2, [0, 1] as int[], IncludeType.MACRO_FUNCTION, "B", [token('X'), token('Y')])
        "B((X), A(Y), C(d()))"        | new ArgsMappingMacroFunction("A", 2, [-2, 0, -2, 1, -1] as int[], IncludeType.MACRO_FUNCTION, "B", [tokens('(X)'), expression('A(Y)'), expression('C(d())')])
        "B(<a.h>, X, Y)"              | new ArgsMappingMacroFunction("A", 2, [-1, 0, 1] as int[], IncludeType.MACRO_FUNCTION, "B", [expression('<a.h>'), token('X'), token('Y')])
        "B(<a.h>,  ( X\t ), (  Z ) )" | new ArgsMappingMacroFunction("A", 2, [-1, -2, 0, -1] as int[], IncludeType.MACRO_FUNCTION, "B", [expression('<a.h>'), tokens('(X)'), tokens('(Z)')])
    }

    def "finds function-like macro directive with multiple parameters whose body is token concatenation of parameters"() {
        when:
        sourceFile << """
#define A(X, Y) ${body}
"""

        then:
        macros.empty
        macroFunctions == [expected]

        where:
        body         | expected
        'X ## Y'     | new ArgsMappingMacroFunction("A", 2, [0, 1] as int[], IncludeType.EXPAND_TOKEN_CONCATENATION, null, [token('X'), token('Y')])
        'X ## OTHER' | new ArgsMappingMacroFunction("A", 2, [0, -1] as int[], IncludeType.EXPAND_TOKEN_CONCATENATION, null, [token('X'), token('OTHER')])
        'OTHER ## X' | new ArgsMappingMacroFunction("A", 2, [-1, 0] as int[], IncludeType.EXPAND_TOKEN_CONCATENATION, null, [token('OTHER'), token('X')])
        'A ## B'     | new ReturnFixedValueMacroFunction("A", 2, IncludeType.EXPAND_TOKEN_CONCATENATION, null, [token('A'), token('B')])
    }

    def "finds function-like macro directive with multiple parameters whose body cannot be resolved"() {
        when:
        sourceFile << """
#define A(X, Y) ${body}
"""

        then:
        macros.empty
        macroFunctions == [unresolvedMacroFunction('A', 2)]

        where:
        body << ['Defined("a.h)', 'A(B(C, (D()))', '"abc" 12 + 5', 'A##~']
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

    def "finds function-like macro directive with multiple parameters with no whitespace between name and body"() {
        when:
        sourceFile << """
#define A(X)Y
"""

        then:
        macros.empty
        macroFunctions == [macroFunction('A', 1, 'Y')]
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
