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
        failure.assertHasErrorOutput("TestGrammar.g:7:24: unexpected token: extra")
        failure.assertHasErrorOutput("TestGrammar.g:9:13: unexpected token: mexpr")
        failure.assertHasErrorOutput("TestGrammar.g:7:24: rule classDef trapped:")
        failure.assertHasErrorOutput("TestGrammar.g:7:24: unexpected token: extra")
        assertAntlrVersion(2)
        failure.assertHasDescription("Execution failed for task ':grammar-builder:generateGrammarSource'.")
        failure.assertHasCause("There were errors during grammar generation")
        failure.assertHasCause("ANTLR Panic: Exiting due to errors.")
    }

    def "uses antlr v2 if no explicit dependency is set"() {
        buildFile.text = buildFile.text.replace("antlr '$antlrDependency'", "")

        goodGrammar()
        goodProgram()

        expect:
        succeeds("generateGrammarSource")
        assertAntlrVersion(2)
        assertGrammarSourceGenerated("org/acme/TestGrammar")
        assertGrammarSourceGenerated("org/acme/AnotherGrammar")
        assertGrammarSourceGenerated("UnpackagedGrammar")
    }

    private goodGrammar() {
        file("grammar-builder/src/main/antlr/TestGrammar.g") << """
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

        file("grammar-builder/src/main/antlr/AnotherGrammar.g") << """
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

        file("grammar-builder/src/main/antlr/UnpackagedGrammar.g") << """class UnpackagedGrammar extends Parser;
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
        file("grammar-user/src/main/java/com/example/test/Test.java") << """
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
        file("grammar-builder/src/main/antlr/TestGrammar.g") << """class TestGrammar extends Parser;
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
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}.java").exists()
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}.smap").exists()
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}TokenTypes.java").exists()
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}TokenTypes.txt").exists()
    }
}
