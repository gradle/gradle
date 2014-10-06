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

    def setup() {
        writeBuildFile()
    }

    def "analyze good grammar"() {
        goodGrammar()

        expect:
        succeeds("generateGrammarSource")
        file("build/generated-src/antlr/main/Test.tokens").exists()
        file("build/generated-src/antlr/main/TestLexer.java").exists()
        file("build/generated-src/antlr/main/TestParser.java").exists()
        assertAntlrVersion(3)
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

    private void writeBuildFile() {
        buildFile << """
            apply plugin: "java"
            apply plugin: "antlr"

            repositories() {
                mavenCentral()
            }

            dependencies {
                antlr 'org.antlr:antlr:3.5.2'
            }
        """
    }
}
