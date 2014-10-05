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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class Antlr4PluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        writeBuildFile()
    }

    def "analyze good grammar"() {
        goodGrammar()

        expect:
        succeeds("generateGrammarSource")
        file("build/generated-src/antlr/main/Test.tokens").exists()
        file("build/generated-src/antlr/main/TestBaseListener.java").exists()
        file("build/generated-src/antlr/main/TestLexer.java").exists()
        file("build/generated-src/antlr/main/TestLexer.tokens").exists()
        file("build/generated-src/antlr/main/TestListener.java").exists()
        file("build/generated-src/antlr/main/TestParser.java").exists()
    }

    def "analyze bad grammar"() {
        badGrammar()

        expect:
        fails("generateGrammarSource")
    }

    private goodGrammar() {
        file("src/main/antlr/Test.g4") << """grammar Test;
            r  : 'hello' ID ;        
            ID : [a-z]+ ;  
            WS : [ \\t\\r\\n]+ -> skip ;
        """
    }

    private badGrammar() {
        file("src/main/antlr/Test.g4") << """grammar Test;
            r  : 'hello' ID ;    extrastuff
            ID : [a-z]+ ;            
            WS : [ \\t\\r\\n]+ -> skip ;
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
                antlr 'org.antlr:antlr4:4.3'
            }

            generateGrammarSource {
                antlrClasspath = files(antlrClasspath.files)
            }
        """
    }
}
