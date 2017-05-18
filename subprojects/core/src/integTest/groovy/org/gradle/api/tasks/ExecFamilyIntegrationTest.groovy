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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec
import spock.lang.Issue
import spock.util.environment.OperatingSystem

class ExecFamilyIntegrationTest extends AbstractConsoleFunctionalSpec {
    private static final String EXPECTED_OUTPUT = "Hello, World!"

    @Issue("https://github.com/gradle/gradle/issues/2009")
    def "Project#javaexec output is grouped with it's task output"() {
        given:
        generateMainJavaFileEchoing(EXPECTED_OUTPUT)
        buildFile << """apply plugin: 'java'

task run {
    dependsOn 'compileJava'
    doLast {
        project.javaexec {
            classpath = sourceSets.main.runtimeClasspath
            main = 'Main'
        }
    }
}
"""

        when:
        succeeds("run")

        then:
        (result.output =~ /(?ms)(> Task :run.*?$EXPECTED_OUTPUT)/).find()
    }

    @Issue("https://github.com/gradle/gradle/issues/2009")
    def "JavaExec task output is grouped with it's task output"() {
        given:
        generateMainJavaFileEchoing(EXPECTED_OUTPUT)
        buildFile << """apply plugin: 'java'

task run(type: JavaExec) {
    dependsOn 'compileJava'
    classpath = sourceSets.main.runtimeClasspath
    main = 'Main'
}
"""

        when:
        succeeds("run")

        then:
        (result.output =~ /(?ms)(> Task :run.*?$EXPECTED_OUTPUT)/).find()
    }

    @Issue("https://github.com/gradle/gradle/issues/2009")
    def "Project#exec output is grouped with it's task output"() {
        given:
        buildFile << """task run {
    doLast {
        project.exec {
            commandLine ${echo(EXPECTED_OUTPUT)}
        }
    }
}
"""

        when:
        succeeds("run")

        then:
        (result.output =~ /(?ms)(> Task :run.*?$EXPECTED_OUTPUT)/).find()
    }

    @Issue("https://github.com/gradle/gradle/issues/2009")
    def "Exec task output is grouped with it's task output"() {
        given:
        buildFile << """task run(type: Exec) {
    commandLine ${echo(EXPECTED_OUTPUT)}
}
"""

        when:
        succeeds("run")

        then:
        (result.output =~ /(?ms)(> Task :run.*?$EXPECTED_OUTPUT)/).find()
    }

    private static String echo(String s) {
        if (OperatingSystem.current.windows) {
            return "'cmd.exe', '/c', 'echo $s'"
        }
        return "'echo', '$s'"
    }

    private void generateMainJavaFileEchoing(String s) {
        file("src/main/java/Main.java") << """public class Main {
    public static void main(String[] args) {
        System.out.println("$s");
    }
}"""
    }
}
