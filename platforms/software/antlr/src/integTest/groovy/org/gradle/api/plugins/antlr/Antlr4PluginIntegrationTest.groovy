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

import org.gradle.test.fixtures.file.TestFile

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
        buildFile "grammar-builder/build.gradle", """
            generateGrammarSource {
                arguments << "-lib" << "src/main/antlr/org/acme"
            }
        """
        then:
        succeeds("generateGrammarSource")
        assertGrammarSourceGenerated("org/acme/Test")
        assertGrammarSourceGenerated("Another")
        assertGrammarSourceGenerated("GrammarWithImport")
        assertAntlrVersion(4)
    }

    def "can generate grammar source with package using #description and correctly wire source set"() {
        goodGrammarWithoutPackage()

        when:
        buildFile "grammar-builder/build.gradle", """
            generateGrammarSource {
                outputDirectory = file("build/sources/antlr/main${packageDir}")
                ${expression}
            }
        """
        if (expectDeprecationWarning) {
            expectPackageArgumentDeprecationWarning(executer)
        }

        then:
        succeeds("generateGrammarSource")

        and:
        assertGrammarSourceGenerated(file("grammar-builder/build/sources/antlr/main"), "org/acme/Test")
        assertGrammarSourceGenerated(file("grammar-builder/build/sources/antlr/main"), "org/acme/Another")
        assertAntlrVersion(4)

        and:
        succeeds("build")

        where:
        description                     | expression                               | packageDir  | expectDeprecationWarning
        "arguments"                     | "arguments += ['-package', 'org.acme']"  | "/org/acme" | true
        "packageName property"          | "packageName = 'org.acme'"               | ""          | false
    }

    def "exception when both packageName and arguments are set"() {
        goodGrammarWithoutPackage()

        when:
        buildFile "grammar-builder/build.gradle", """
            generateGrammarSource {
                packageName = 'org.acme'
                arguments += ['-package', 'org.acme']
            }
        """
        expectPackageArgumentDeprecationWarning(executer)

        then:
        fails("generateGrammarSource")
        failure.assertHasCause("The package has been set both in the arguments (i.e. '-package') and via the 'packageName' property.  Please set the package only using the 'packageName' property.")
    }

    def "can change output directory and source set reflects change"() {
        goodGrammar()
        goodProgram()
        buildFile "grammar-builder/build.gradle", """
            generateGrammarSource {
                outputDirectory = file("build/generated/antlr/main")
            }
        """

        when:
        expect:
        succeeds("generateGrammarSource")
        assertGrammarSourceGenerated(file("grammar-builder/build/generated/antlr/main"),"org/acme/Test")
        assertGrammarSourceGenerated(file("grammar-builder/build/generated/antlr/main"), "Another")
        assertAntlrVersion(4)
        succeeds("build")
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
        assertGrammarSourceGenerated(file('grammar-builder/build/generated-src/antlr/main'), grammarName)
    }

    private static void assertGrammarSourceGenerated(TestFile root, String grammarName) {
        assert root.file("${grammarName}.tokens").exists()
        assert root.file("${grammarName}BaseListener.java").exists()
        assert root.file("${grammarName}Lexer.java").exists()
        assert root.file("${grammarName}Lexer.tokens").exists()
        assert root.file("${grammarName}Listener.java").exists()
        assert root.file("${grammarName}Parser.java").exists()
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

    private goodGrammarWithoutPackage() {
        file("grammar-builder/src/main/antlr/Test.g4") << """grammar Test;
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
}
