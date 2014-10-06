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

    def setup() {
        writeBuildFile()
    }

    def "analyze good grammar"() {
        goodGrammar()

        expect:
        succeeds("generateGrammarSource")
        assertAntlrVersion(2)
        file("build/generated-src/antlr/main/Test.java").exists()
        file("build/generated-src/antlr/main/Test.smap").exists()
        file("build/generated-src/antlr/main/TestTokenTypes.java").exists()
        file("build/generated-src/antlr/main/TestTokenTypes.txt").exists()
    }

    def "analyze bad grammar"() {
        badGrammar()

        expect:
        args "-i"
        fails("generateGrammarSource")
        assertAntlrVersion(2)
    }

    private goodGrammar() {
        file("src/main/antlr/Test.g") << """class Test extends Parser;
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

    private badGrammar() {
        file("src/main/antlr/Test.g") << """class Test extends Parser;
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

    private void writeBuildFile() {
        buildFile << """
            apply plugin: "java"
            apply plugin: "antlr"

            repositories {
              jcenter()
            }
        """
    }
}
