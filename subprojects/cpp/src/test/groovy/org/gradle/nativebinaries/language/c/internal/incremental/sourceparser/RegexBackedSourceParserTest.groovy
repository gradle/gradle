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
package org.gradle.nativebinaries.language.c.internal.incremental.sourceparser

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class RegexBackedSourceParserTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    SourceParser parser = new RegexBackedSourceParser()

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
        return parsedSource.quotedIncludes
    }

    def getSystemIncludes() {
        return parsedSource.systemIncludes
    }

    def getImports() {
        return parsedSource.quotedImports
    }

    def getSystemImports() {
        return parsedSource.systemImports
    }

    def getFound() {
        return includes + systemIncludes + imports + systemImports
    }
    
    def noIncludes() {
        assert includes == []
        assert systemIncludes == []
        true
    }
    
    def noImports() {
        assert imports == []
        assert systemImports == []
        true
    }

    def useDirective(String directive) {
        sourceFile.text = sourceFile.text.replace("include", directive)
    }

    def "parses file with no includes"() {
        when:
        sourceFile << ""

        then:
        includes == []
        systemIncludes == []
        
        and:
        noImports()
    }

    def "finds quoted include"() {
        when:
        sourceFile << """
    #include "test.h"
"""

        then:
        includes == ["test.h"]
        systemIncludes == []
        
        and:
        noImports()
    }

    def "finds system include"() {
        when:
        sourceFile << """
    #include <test.h>
"""

        then:
        includes == []
        systemIncludes == ["test.h"]
        
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
"""
        then:
        includes == ["test1", "test2"]
        systemIncludes == ["system1", "system2"]
        
        and:
        noImports()
    }

    def "finds quoted import"() {
        when:
        sourceFile << """
            #import "test.h"
        """

        then:
        imports == ["test.h"]
        systemImports == []
        
        and:
        noIncludes()
    }

    def "finds system import"() {
        when:
        sourceFile << """
            #import <test.h>
        """

        then:
        imports == []
        systemImports == ["test.h"]
        
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
"""
        then:
        imports == ["test1", "test2"]
        systemImports == ["system1", "system2"]
        
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
"""
        then:
        includes == ["test2", "test4"]
        systemIncludes == ["system3"]
        imports == ["test1", "test3"]
        systemImports == ["system1", "system2", "system4"]
    }

    @Unroll
    def "finds #directive surrounded by different whitespace"() {
        when:
        sourceFile << """
#include     "test1"
#include\t"test2"\t
\t#include\t"test3"
#include     <system1>
#include\t<system2>\t
\t#include\t<system3>
"""
        and:
        useDirective(directive)

        then:
        found == ["test1", "test2", "test3", "system1", "system2", "system3"]

        where:
        directive << ["include", "import"]
    }

    @Unroll
    def "finds #directive where whitespace surrounds the # character"() {
        when:
        sourceFile << """
  #  include   "test1"
\t#\tinclude "test2"

  #  include   <system1>
\t#\tinclude <system2>
"""
        and:
        useDirective(directive)

        then:
        found == ["test1", "test2", "system1", "system2"]

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
        includes == ["test1", "test2", "test3"]
        systemIncludes == ["system1", "system2", "system3"]
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
/* a comment here*/#include/* a comment here*/<system3>
"""
        useDirective(directive)

        then:
        found == ["test1", "test2", "test3", "system1", "system2", "system3"]

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
        includes == [included]
        imports == [included]

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
        systemIncludes == [included]
        systemImports == [included]

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
        includes == ["test1", "test2", "test3"]
    }
}
