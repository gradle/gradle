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

class Antlr4PluginIntegrationTest extends AbstractAntlrIntegrationTest {

    String antlrDependency = "org.antlr:antlr4:4.3"

    def "analyze good grammar"() {
        goodGrammar()
        goodProgram()
        expect:
        succeeds("generateGrammarSource")
        assertGrammarSourceGenerated("org/acme/Test")
        assertGrammarSourceGenerated("Another")
        assertAntlrVersion(4)
        succeeds("build")
    }

    def "can import grammar from root antlr source folder"() {
        goodGrammar()
        file("grammar-builder/src/main/antlr/GrammarWithImport.g4") << """grammar GrammarWithImport;
            import Another;
            r  : 'hello' ID ;
            ID : [a-z]+ ;
            WS : [ \\t\\r\\n]+ -> skip ;
        """

        expect:
        succeeds("generateGrammarSource")
        assertGrammarSourceGenerated("org/acme/Test")
        assertGrammarSourceGenerated("Another")
        assertGrammarSourceGenerated("GrammarWithImport")
        assertAntlrVersion(4)
    }

    def "can import grammar from non root folder using -lib argument"() {
        goodGrammar()
        file("grammar-builder/src/main/antlr/GrammarWithImport.g4") << """grammar GrammarWithImport;
            import Test;
            r  : 'hello' ID ;
            ID : [a-z]+ ;
            WS : [ \\t\\r\\n]+ -> skip ;
        """
        when:
        buildFile << """
        project(":grammar-builder") {
            generateGrammarSource {
                arguments.add("-lib")
                arguments.add("src/main/antlr/org/acme")
            }
        }
        """
        then:
        succeeds("generateGrammarSource")
        assertGrammarSourceGenerated("org/acme/Test")
        assertGrammarSourceGenerated("Another")
        assertGrammarSourceGenerated("GrammarWithImport")
        assertAntlrVersion(4)
    }

    void goodProgram() {
        file("grammar-user/src/main/java/Main.java") << """

        import org.antlr.v4.runtime.ANTLRInputStream;
        import org.antlr.v4.runtime.CommonTokenStream;
        import java.io.IOException;
        import java.io.StringReader;
        import org.acme.TestLexer;
        import org.acme.TestParser;

        public class Main {
            public static void main(String[] args) throws IOException {
                TestLexer l = new TestLexer(new ANTLRInputStream(new StringReader("test")));
                TestParser p = new TestParser(new CommonTokenStream(l));
            }
        }
        """

    }

    private void assertGrammarSourceGenerated(String grammarName) {
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}.tokens").exists()
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}BaseListener.java").exists()
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}Lexer.java").exists()
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}Lexer.tokens").exists()
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}Listener.java").exists()
        assert file("grammar-builder/build/generated-src/antlr/main/${grammarName}Parser.java").exists()
    }

    def "analyze bad grammar"() {
        badGrammar()

        expect:
        fails("generateGrammarSource")
        failure.assertHasCause("There was 1 error during grammar generation")
        assertAntlrVersion(4)
    }

    private goodGrammar() {
        file("grammar-builder/src/main/antlr/org/acme/Test.g4") << """grammar Test;
            @header {
                package org.acme;
            }
            r  : 'hello' ID ;
            ID : [a-z]+ ;
            WS : [ \\t\\r\\n]+ -> skip ;
        """

        file("grammar-builder/src/main/antlr/Another.g4") << """grammar Another;
            r  : 'hello' ID ;
            ID : [a-z]+ ;
            WS : [ \\t\\r\\n]+ -> skip ;
        """
    }

    private badGrammar() {
        file("grammar-builder/src/main/antlr/Test.g4") << """grammar Test;
            r  : 'hello' ID ;    extrastuff
            ID : [a-z]+ ;
            WS : [ \\t\\r\\n]+ -> skip ;
        """
    }
}
