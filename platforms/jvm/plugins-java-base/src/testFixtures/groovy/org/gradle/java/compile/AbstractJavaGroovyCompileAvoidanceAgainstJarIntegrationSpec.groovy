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

package org.gradle.java.compile

import groovy.transform.SelfType
import spock.lang.Issue

@SelfType(AbstractJavaGroovyCompileAvoidanceIntegrationSpec)
trait AbstractJavaGroovyCompileAvoidanceAgainstJarIntegrationSpec {
    def "doesn't recompile when implementation manifest is changed"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl { public void execute() { int i = 12; } }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        buildFile << """
            project(':a') {
                jar.manifest.attributes(attr1: 'value')
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ':a:jar'
        skipped ":b:${language.compileTaskName}"
    }

    @Issue("gradle/gradle#1457")
    def "doesn't fail when jar is missing"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
            project(':a') {
                jar.enabled = false
            }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        noExceptionThrown()
    }

}
