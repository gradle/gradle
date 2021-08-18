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

import org.gradle.integtests.fixtures.AbstractProjectRelocationIntegrationTest
import org.gradle.test.fixtures.file.TestFile

class Antlr3RelocationIntegrationTest extends AbstractProjectRelocationIntegrationTest {
    @Override
    protected String getTaskName() {
        return ":generateGrammarSource"
    }

    @Override
    protected void setupProjectIn(TestFile projectDir) {
        projectDir.file("src/main/antlr/org/acme/test/Test.g") << """grammar Test;
            @header {
                package org.acme.test;
            }
            @lexer::header {
                package org.acme.test;
            }

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
        projectDir.file("build.gradle") << """
            apply plugin: "antlr"

            ${mavenCentralRepository()}

            dependencies {
                antlr 'org.antlr:antlr:3.5.2'
            }
        """
    }

    @Override
    protected extractResultsFrom(TestFile projectDir) {
        def result = [
            "Test.tokens",
            "org/acme/test/TestLexer.java",
            "org/acme/test/TestParser.java",
        ]
            .collect { projectDir.file("build/generated-src/antlr/main/$it").text.split(/\n/) }
            .flatten()
            .findAll { !(it =~ "// \\\$ANTLR") }
            .join("\n")
    }
}
