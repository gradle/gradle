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

class Antlr4RelocationIntegrationTest extends AbstractProjectRelocationIntegrationTest {
    @Override
    protected String getTaskName() {
        return ":generateGrammarSource"
    }

    @Override
    protected void setupProjectIn(TestFile projectDir) {
        projectDir.file("src/main/antlr/org/acme/Test.g4") << """grammar Test;
            @header {
                package org.acme;
            }
            r  : 'hello' ID ;
            ID : [a-z]+ ;
            WS : [ \\t\\r\\n]+ -> skip ;
        """
        projectDir.file("build.gradle") << """
            apply plugin: "antlr"

            ${mavenCentralRepository()}

            dependencies {
                antlr 'org.antlr:antlr4:4.3'
            }
        """
    }

    @Override
    protected extractResultsFrom(TestFile projectDir) {
        [
            "org/acme/Test.tokens",
            "org/acme/TestBaseListener.java",
            "org/acme/TestLexer.java",
            "org/acme/TestLexer.tokens",
            "org/acme/TestListener.java",
            "org/acme/TestParser.java",
        ].collect { projectDir.file("build/generated-src/antlr/main/$it").text }.join("\n")
    }
}
