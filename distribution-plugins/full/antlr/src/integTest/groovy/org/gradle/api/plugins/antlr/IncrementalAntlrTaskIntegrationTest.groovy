/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.plugins.antlr

import static org.gradle.integtests.fixtures.WaitAtEndOfBuildFixture.buildLogicForMinimumBuildTime

class IncrementalAntlrTaskIntegrationTest extends AbstractAntlrIntegrationTest {
    String antlrDependency = "org.antlr:antlr:3.5.2"

    def test1TokenFile = file("grammar-builder/build/generated-src/antlr/main/Test1.tokens")
    def test1LexerFile = file("grammar-builder/build/generated-src/antlr/main/Test1Lexer.java")
    def test1ParserFile = file("grammar-builder/build/generated-src/antlr/main/Test1Parser.java")

    def test2TokenFile = file("grammar-builder/build/generated-src/antlr/main/Test2.tokens")
    def test2LexerFile = file("grammar-builder/build/generated-src/antlr/main/Test2Lexer.java")
    def test2ParserFile = file("grammar-builder/build/generated-src/antlr/main/Test2Parser.java")

    def setup() {
        buildFile << buildLogicForMinimumBuildTime(2000)
    }

    def "changed task inputs handled incrementally"() {
        when:
        grammar("Test1", "Test2")
        then:
        succeeds("generateGrammarSource")

        when:
        def test1TokensFileSnapshot = test1TokenFile.snapshot()
        def test1LexerFileSnapshot = test1LexerFile.snapshot()
        def test1ParserFileSnapshot = test1ParserFile.snapshot()

        def test2TokensFileSnapshot = test2TokenFile.snapshot()
        def test2LexerFileSnapshot = test2LexerFile.snapshot()
        def test2ParserFileSnapshot = test2ParserFile.snapshot()

        changedGrammar("Test2")

        then:
        succeeds("generateGrammarSource")
        test1TokenFile.assertHasNotChangedSince(test1TokensFileSnapshot)
        test1LexerFile.assertHasNotChangedSince(test1LexerFileSnapshot)
        test1ParserFile.assertHasNotChangedSince(test1ParserFileSnapshot)

        test2TokenFile.assertHasChangedSince(test2TokensFileSnapshot)
        test2LexerFile.assertHasChangedSince(test2LexerFileSnapshot)
        test2ParserFile.assertHasChangedSince(test2ParserFileSnapshot)
    }

    def "added grammar is handled incrementally"() {
        when:
        grammar("Test1")
        then:
        succeeds("generateGrammarSource")

        when:
        def test1TokensFileSnapshot = test1TokenFile.snapshot()
        def test1LexerFileSnapshot = test1LexerFile.snapshot()
        def test1ParserFileSnapshot = test1ParserFile.snapshot()

        !test2TokenFile.exists()
        !test2LexerFile.exists()
        !test2ParserFile.exists()

        grammar("Test2")

        then:
        succeeds("generateGrammarSource")
        test1TokenFile.assertHasNotChangedSince(test1TokensFileSnapshot)
        test1LexerFile.assertHasNotChangedSince(test1LexerFileSnapshot)
        test1ParserFile.assertHasNotChangedSince(test1ParserFileSnapshot)

        test2TokenFile.exists()
        test2LexerFile.exists()
        test2ParserFile.exists()

    }

    def "rerun when arguments changed"() {
        when:
        grammar("Test1")
        then:
        succeeds("generateGrammarSource")

        when:
        def test1TokensFileSnapshot = test1TokenFile.snapshot()
        def test1LexerFileSnapshot = test1LexerFile.snapshot()
        def test1ParserFileSnapshot = test1ParserFile.snapshot()

        buildFile << """
            project(":grammar-builder") {
                generateGrammarSource {
                    arguments << '-dfa'
                }
            }
        """

        then:
        succeeds("generateGrammarSource")
        test1TokenFile.assertHasChangedSince(test1TokensFileSnapshot)
        test1LexerFile.assertHasChangedSince(test1LexerFileSnapshot)
        test1ParserFile.assertHasChangedSince(test1ParserFileSnapshot)
    }

    def "output for removed grammar file is handled correctly"() {
        when:
        grammar("Test1", "Test2")
        then:
        succeeds("generateGrammarSource")

        test1TokenFile.exists()
        test1LexerFile.exists()
        test1ParserFile.exists()

        test2TokenFile.exists()
        test2LexerFile.exists()
        test2ParserFile.exists()

        when:
        removedGrammar("Test1")

        then:
        succeeds("generateGrammarSource")
        !test1TokenFile.exists()
        !test1LexerFile.exists()
        !test1ParserFile.exists()
    }

    def grammar(String... ids) {
        ids.each { id ->
            file("grammar-builder/src/main/antlr/${id}.g") << """grammar ${id};
            list    :   item (item)*
                    ;

            item    :
                ID
                | INT
                ;

            ID  :   ('a'..'z'|'_') ('a'..'z'|'0'..'9'|'_')*
                ;

            INT :   '0'..'9'+
                ;
        """
        }
    }

    def changedGrammar(String... ids) {
        ids.each { id ->
            file("grammar-builder/src/main/antlr/${id}.g").text = """
/** Comment to ensure the file length is changed */
grammar ${id};
             list    :   item (item)*
                    ;

            item    :
                ID
                | INT
                ;

            ID  :   ('A'..'Z'|'_') ('A'..'Z'|'0'..'9'|'_')*
                ;

            INT :   '0'..'9'+
                ;
        """
        }
    }

    def removedGrammar(String... ids) {
        ids.each { id ->
            file("grammar-builder/src/main/antlr/${id}.g").delete()
        }
    }
}
