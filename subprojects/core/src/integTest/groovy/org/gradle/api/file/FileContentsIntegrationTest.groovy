/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import java.nio.charset.Charset

@Issue("https://github.com/gradle/gradle/issues/15802")
class FileContentsIntegrationTest extends AbstractIntegrationSpec  {

    static final String TURKISH_TEXT = "Ğğ, İ, ı, Şş"

    def "succeeds with Turkish encoding"() {
        given:
        testDirectory.file("foo.txt").write("Hello: " + TURKISH_TEXT, "ISO-8859-9");
        buildKotlinFile << createScript("ISO-8859-9")

        when: "1st run"
        succeeds "consumer"

        then:
        outputContains(TURKISH_TEXT)
    }

    def "fails with wrong encoding"() {
        given:
        testDirectory.file("foo.txt").write("Hello: " + TURKISH_TEXT, "ISO-8859-9");
        buildKotlinFile << createScript("KOI8-R")

        when: "1st run"
        succeeds "consumer"

        then:
        outputDoesNotContain(TURKISH_TEXT)
        outputContains(new String(TURKISH_TEXT.getBytes("ISO-8859-9"), "KOI8-R"))
    }

    def "succeeds with default encoding"() {
        given:
        testDirectory.file("foo.txt").write("Hello: " + TURKISH_TEXT, Charset.defaultCharset().toString());
        buildKotlinFile << createScript()

        when: "1st run"
        succeeds "consumer"

        then:
        outputContains(TURKISH_TEXT)
    }

    private static String createScript(String encoding = null) {
        String macro = encoding != null ? "\"${encoding}\"" : ""
        return """
            abstract class Consumer : DefaultTask() {
                @get:Input
                abstract val text: Property<String>

                @TaskAction
                fun consume() {
                    project.logger.lifecycle("Got message: " + text.get())
                }
            }

            tasks.register<Consumer>("consumer") {
                text = providers.fileContents(layout.projectDirectory.file("foo.txt"))
                    .getAsText(${macro})
            }

        """.stripIndent()
    }

}
