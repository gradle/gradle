/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class AbstractIncrementalJavaCompileIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
include 'a', 'b'
"""
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
        """
    }

    def useIncrementalCompile() {
        buildFile << """
            allprojects {
                tasks.withType(JavaCompile) {
                    options.incremental = true
                }
            }
        """
    }

    def useJar() {
        buildFile << """
            allprojects {
                tasks.withType(JavaCompile) {
                    // Use forking to work around javac's jar cache
                    options.fork = true
                }
            }
"""
    }

    def useClassesDir() {
        buildFile << """
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(Attribute.of('usage', String))
                    }
                }
                configurations.compile {
                    attribute 'usage', 'compile'
                }
                configurations.compileClasspath {
                    attribute 'usage', 'compile'
                    canBeConsumed = false
                }
                artifacts {
                    compile file: compileJava.destinationDir, builtBy: compileJava
                }
                jar.doFirst { throw new RuntimeException('should not be using this') }
                processResources.doFirst { throw new RuntimeException('should not be using this') }
            }
        """
    }

    def "doesn't recompile when implementation class changes in ABI compatible way"() {
        given:
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
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
        sourceFile.text = """
            public class ToolImpl { public void execute() { String s = toString(); } }
"""

        then:
        succeeds ':b:compileJava'
        executedAndNotSkipped ':a:compileJava'
        skipped ':b:compileJava'

        when:
        sourceFile.text = """
            public class ToolImpl { public static ToolImpl instance; public void execute() { String s = toString(); } }
"""

        then:
        succeeds ':b:compileJava'
        executedAndNotSkipped ':a:compileJava', ":b:compileJava"
    }

    def "doesn't recompile when implementation resource is changed"() {
        given:
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
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
        def resourceFile = file("a/src/main/resources/a.properties")
        resourceFile.text = "a = 12"
        file("b/src/main/java/Main.java") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ':b:compileJava'

        then:
        executedAndNotSkipped ':a:compileJava'
        executedAndNotSkipped ':b:compileJava'

        when:
        resourceFile.text = "a = 11"

        then:
        succeeds ':b:compileJava'
        skipped ':a:compileJava'
        skipped ':b:compileJava'
    }
}
