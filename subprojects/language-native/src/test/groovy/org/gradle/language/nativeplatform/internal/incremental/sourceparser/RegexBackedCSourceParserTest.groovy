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

import org.gradle.language.nativeplatform.internal.Include
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class RegexBackedCSourceParserTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    CSourceParser parser = new RegexBackedCSourceParser()

    protected TestFile getSourceFile() {
        testDirectory.file('source.c')
    }

    TestFile getTestDirectory() {
        temporaryFolder.testDirectory
    }

    def getParsedSource() {
        parser.parseSource(sourceFile)
    }

    def getIncludes() {
        return parsedSource.includesOnly
    }

    def getImports() {
        return parsedSource.includesAndImports - parsedSource.includesOnly
    }

    def getFound() {
        return parsedSource.includesAndImports.collect { it.value }
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

    Include include(String value, boolean isImport = false) {
        return DefaultInclude.parse(value, isImport)
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
        includes == ['"test.h"'].collect { include(it) }

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
        includes == ['<test.h>'].collect { include(it) }
        
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

    def "finds defined include"() {
        when:
        sourceFile << """
    #include DEFINED
"""

        then:
        includes == ['DEFINED'].collect { include(it) }

        and:
        noImports()
    }

    def "finds multiple includes"() {
        when:
        sourceFile << """
    #include "test1"
    #include "test2"
    #include <system1>
    #include <system2>
    #include DEFINED
"""
        then:
        includes == ['"test1"', '"test2"', '<system1>', '<system2>', 'DEFINED'].collect { include(it) }
        
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
"""
        then:
        includes == ['"test1"', '"test2"', '"test3"', '<system1>', '<system2>', '<system3>'].collect { include(it) }
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

    def "find various defined includes"() {
        when:
        sourceFile << """
    #include $included
    #import $included
"""
        then:
        includes == [included].collect { include(it) }
        imports == [included].collect { include(it, true) }

        where:
        included << ["DEFINED", "mixedDefined", "DEF_INED", "_DEFINED", "__DEFINED__"]
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

        where:
        directive << ["include", "import"]
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

        where:
        directive << ["include", "import"]
    }

    def "ignores badly formed directives"() {
        when:
        sourceFile << """
include
#
# include
# import

void # include <thing>

#import <

# inklude <thing.h>

#import thing.h
#import thing.h"
#import "thing.h>
#include <thing.h
#include "thing.h

#include
<thing.h>

#include 'thing.h' extra stuff

"""

        then:
        noIncludes()
        noImports()
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
}
