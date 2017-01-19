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

abstract class AbstractJavaCompileAvoidanceAgainstJarIntegrationSpec extends AbstractJavaCompileAvoidanceIntegrationSpec {
    def setup() {
        useJar()
    }

    def "doesn't recompile when implementation manifest is changed"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/java/ToolImpl.java")
        sourceFile << """
            public class ToolImpl { public void execute() { int i = 12; } }
        """
        file("b/src/main/java/Main.java") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ':b:compileJava'

        then:
        executedAndNotSkipped ':a:compileJava'
        executedAndNotSkipped ':b:compileJava'

        when:
        buildFile << """
            project(':a') {
                jar.manifest.attributes(attr1: 'value')
            }
"""

        then:
        succeeds ':b:compileJava'
        executedAndNotSkipped ':a:jar'
        skipped ':b:compileJava'
    }

}
