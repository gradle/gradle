/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest

class AntlrRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {
    @Override
    protected String getTaskName() {
        return ":generateGrammarSource"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        file("src/main/antlr/org/acme/TestGrammar.g") << """ class TestGrammar extends Parser;
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
        buildFile << buildFileWithSourceDir("src/main/antlr")
    }

    private static String buildFileWithSourceDir(String sourceDir) {
        """
            apply plugin: "antlr"

            ${jcenterRepository()}

            generateGrammarSource {
                source "$sourceDir"
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        file("src").renameTo(file("other-src"))
        buildFile.text = buildFileWithSourceDir("other-src/main/antlr")
    }

    @Override
    protected extractResults() {
        [
            "build/generated-src/antlr/main/TestGrammar.java",
            "build/generated-src/antlr/main/TestGrammar.smap",
            "build/generated-src/antlr/main/TestGrammarTokenTypes.java",
            "build/generated-src/antlr/main/TestGrammarTokenTypes.txt",
        ].collect { file(it).text }.join("\n")
    }
}
