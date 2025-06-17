/*
 * Copyright 2025 the original author or authors.
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
import spock.lang.Issue

class AntlrFilteringIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/33180")
    def "can filter antlr sources in a java source set"() {
        given:
        file('src/main/antlr/Hello.g4') << """
            grammar Hello;
            r   : 'hello' ID;
            ID  : [a-z]+ ;
            WS  : [ \\t\\r\\n]+ -> skip ;
        """.stripIndent()

        buildFile """
            plugins {
                id 'antlr'
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                antlr 'org.antlr:antlr4:4.13.2'
            }

            tasks.named('generateGrammarSource').configure {
                outputDirectory = layout.buildDirectory.file("generated-src/antlr/main/com/company").get().asFile
                arguments = ['-visitor', '-no-listener', '-package', 'com.company']
            }

            tasks.register("filteredTask", SourceTask) {
                source = sourceSets.main.getAllJava()
                exclude('**/com/company/Hello*')
                doLast {
                    println("Filtered task executed with sources:")
                    source.files.each { file ->
                        println(" - " + relativePath(file))
                    }

                }
            }
        """

        when:
        succeeds(":filteredTask")

        then:
        result.assertTaskExecuted(':generateGrammarSource')
        result.assertTaskSkipped(':filteredTask')
    }
}
