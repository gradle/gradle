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

class Antlr3PluginIntegrationTest extends AbstractAntlrIntegrationTest {

    String antlrDependency = "org.antlr:antlr:3.5.2"

    def "analyze good grammar"() {
        goodGrammar()

        expect:
        succeeds("generateGrammarSource")

        assertGrammarSourceGenerated("Test")
        assertGrammarSourceGenerated("AnotherGrammar")
        assertAntlrVersion(3)
    }

    private void assertGrammarSourceGenerated(String grammarName) {
        assert file("build/generated-src/antlr/main/${grammarName}.tokens").exists()
        assert file("build/generated-src/antlr/main/${grammarName}Lexer.java").exists()
        assert file("build/generated-src/antlr/main/${grammarName}Parser.java").exists()
    }

    def "analyze bad grammar"() {
        badGrammar()

        expect:
        fails("generateGrammarSource")
        assertAntlrVersion(3)
    }

    private goodGrammar() {
        file("src/main/antlr/Test.g") << """grammar Test;
            list    :   item (item)*
                    ;

            item    :   
                ID
                | INT
                ;

            ID  :   ('a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'0'..'9'|'_')*
                ;

            INT :   '0'..'9'+
                ;
        """

        file("src/main/antlr/AnotherGrammar.g") << """grammar AnotherGrammar;
            list    :   item (item)*
                    ;

            item    :
                ID
                | INT
                ;

            ID  :   ('a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'0'..'9'|'_')*
                ;

            INT :   '0'..'9'+
                ;
        """
    }

    private badGrammar() {
        file("src/main/antlr/Test.g") << """grammar Test;
            list    :   item (item)*
                    ; some extra stuff

            item    :   
                ID
                | INT
                ;

            ID  :   ('a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'0'..'9'|'_')*
                ;

            INT :   '0'..'9'+
                ;
        """
    }
}
