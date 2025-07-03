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

class AntlrFilteringIntegrationTest extends AbstractIntegrationSpec implements AntlrDeprecationFixture{

    @Issue("https://github.com/gradle/gradle/issues/33180")
    def "can filter antlr sources in a java source set when setting package using #description"() {
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
                arguments = ['-visitor', '-no-listener']
                ${expression}
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
        if (expectDeprecationWarning) {
            expectPackageArgumentDeprecationWarning(executer)
        }
        succeeds(":filteredTask")

        then:
        file("build/generated-src/antlr/main/com/company/HelloParser.java").exists()
        result.assertTaskExecuted(':generateGrammarSource')
        result.assertTaskSkipped(':filteredTask')

        where:
        description                     | expression                             | expectDeprecationWarning
        "arguments and outputDirectory" | setPackageWithArguments("com.company") | true
        "packageName property"          | setPackageWithProperty("com.company")  | false
    }

    static String setPackageWithArguments(String packageName) {
        return """
            outputDirectory = new File(outputDirectory, '${packageName.replace('.', '/')}')
            arguments += ['-package', '${packageName}']
        """
    }

    static String setPackageWithProperty(String packageName) {
        return """
            packageName = '${packageName}'
        """
    }
}
