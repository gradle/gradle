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

import org.gradle.util.TextUtil

class Antlr2PluginIntegrationTest extends AbstractAntlrIntegrationTest {

    String antlrDependency = "antlr:antlr:2.7.7"

    def "analyze and build good grammar"() {
        goodGrammar()
        goodProgram()

        expect:
        succeeds("generateGrammarSource")
        assertAntlrVersion(2)
        assertGrammarSourceGenerated("org/acme/TestGrammar")
        assertGrammarSourceGenerated("org/acme/AnotherGrammar")
        assertGrammarSourceGenerated("UnpackagedGrammar")
        succeeds("build")
    }

    def "analyze bad grammar"() {
        when:
        badGrammar()
        then:
        fails("generateGrammarSource")
        output.contains("TestGrammar.g:7:24: unexpected token: extra")
        output.contains("TestGrammar.g:9:13: unexpected token: mexpr")
        output.contains("TestGrammar.g:7:24: rule classDef trapped:")
        output.contains("TestGrammar.g:7:24: unexpected token: extra")
        assertAntlrVersion(2)
        errorOutput.contains(TextUtil.toPlatformLineSeparators("""
* What went wrong:
Execution failed for task ':generateGrammarSource'.
> There was 1 error during grammar generation
   > ANTLR Panic: Exiting due to errors.
"""))
    }

    def "uses antlr v2 if no explicit dependency is set"() {
        buildFile.text = """
            apply plugin: "java"
            apply plugin: "antlr"

            repositories() {
                jcenter()
            }"""

        goodGrammar()
        goodProgram()

        expect:
        succeeds("generateGrammarSource")
        assertAntlrVersion(2)
        assertGrammarSourceGenerated("org/acme/TestGrammar")
        assertGrammarSourceGenerated("org/acme/AnotherGrammar")
        assertGrammarSourceGenerated("UnpackagedGrammar")

        succeeds("build")
    }

    private goodGrammar() {
        file("src/main/antlr/TestGrammar.g") << """
            header {
                package org.acme;
            }

            class TestGrammar extends Parser;

            options {
                buildAST = true;
            }

            expr:   mexpr (PLUS^ mexpr)* SEMI!
                ;

            mexpr
                :   atom (STAR^ atom)*
                ;

            atom:   INT
                ;"""

        file("src/main/antlr/AnotherGrammar.g") << """
            header {
                package org.acme;
            }
            class AnotherGrammar extends Parser;
            options {
                buildAST = true;
                importVocab = TestGrammar;
            }

            expr:   mexpr (PLUS^ mexpr)* SEMI!
                ;

            mexpr
                :   atom (STAR^ atom)*
                ;

            atom:   INT
                ;"""

        file("src/main/antlr/UnpackagedGrammar.g") << """class UnpackagedGrammar extends Parser;
            options {
                buildAST = true;
            }

            expr:   mexpr (PLUS^ mexpr)* SEMI!
                ;

            mexpr
                :   atom (STAR^ atom)*
                ;

            atom:   INT
                ;"""

    }

    private goodProgram() {
        file("src/main/java/com/example/test/Test.java") << """
            import antlr.Token;
            import antlr.TokenStream;
            import antlr.TokenStreamException;
            import org.acme.TestGrammar;

            public class Test {
                public static void main(String[] args) {
                    TestGrammar parser = new TestGrammar(new DummyTokenStream());
                }

                private static class DummyTokenStream implements TokenStream {
                    public Token nextToken() throws TokenStreamException {
                        return null;
                    }
                }
            }
        """
    }

    private badGrammar() {
        file("src/main/antlr/TestGrammar.g") << """class TestGrammar extends Parser;
            options {
                buildAST = true;
            }

            expr:   mexpr (PLUS^ mexpr)* SEMI!
                ; some extra stuff

            mexpr
                :   atom (STAR^ atom)*
                ;

            atom:   INT
                ;"""
    }

    private void assertGrammarSourceGenerated(String grammarName) {
        assert file("build/generated-src/antlr/main/${grammarName}.java").exists()
        assert file("build/generated-src/antlr/main/${grammarName}.smap").exists()
        assert file("build/generated-src/antlr/main/${grammarName}TokenTypes.java").exists()
        assert file("build/generated-src/antlr/main/${grammarName}TokenTypes.txt").exists()
    }
}
