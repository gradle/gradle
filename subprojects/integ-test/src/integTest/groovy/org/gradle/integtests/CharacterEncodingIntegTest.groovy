/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Unroll

import java.nio.charset.Charset

@IgnoreIf({ GradleContextualExecuter.embedded })
class CharacterEncodingIntegTest extends AbstractIntegrationSpec {

    def executerEncoding(String inputEncoding) {
        if (inputEncoding) {
            executer.withDefaultCharacterEncoding(inputEncoding)
        }
    }

    @Unroll("build default encoding matches specified - input = #inputEncoding, expectedEncoding: #expectedEncoding")
    def "build default encoding matches specified"(String inputEncoding, String expectedEncoding) {
        given:
        executerEncoding inputEncoding

        and:
        buildFile.write """
            task echoDefaultEncoding {
                doFirst {
                    println "default encoding: " + java.nio.charset.Charset.defaultCharset().name()
                }
            }
        """, expectedEncoding

        when:
        run "echoDefaultEncoding"

        then:
        output.contains "default encoding: $expectedEncoding"

        where:
        inputEncoding | expectedEncoding
        "UTF-8"       | "UTF-8"
        "US-ASCII"    | "US-ASCII"
        null          | Charset.defaultCharset().name()
    }

    @Unroll("forked java processes inherit default encoding - input = #inputEncoding, expectedEncoding: #expectedEncoding")
    def "forked java processes inherit default encoding"() {
        given:
        executerEncoding inputEncoding

        and:
        file("src", "main", "java").mkdirs()
        file("src", "main", "java", "EchoEncoding.java").write """
            package echo;

            import java.nio.charset.Charset;

            public class EchoEncoding {
                public static void main(String[] args) {
                    System.out.println("default encoding: " + Charset.defaultCharset().name());
                }
            }
        """, executer.getDefaultCharacterEncoding()

        
        and:
        buildFile.write """
            apply plugin: "java"

            task echoDefaultEncoding(type: JavaExec) {
                classpath = project.files(compileJava)
                main "echo.EchoEncoding"
            }
        """, expectedEncoding

        when:
        run "echoDefaultEncoding"

        then:
        output.contains "default encoding: $expectedEncoding"

        where:
        inputEncoding | expectedEncoding
        "UTF-8"       | "UTF-8"
        "US-ASCII"    | "US-ASCII"
        null          | Charset.defaultCharset().name()
    }


}
