/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.checkstyle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(TestExecutionPreconditions.IsEmbeddedExecutor)
class XdclCheckstyleDemoIntegrationTest extends AbstractIntegrationSpec {

    private void applyEcosystems(String rootName = null) {
        file('settings.gradle.xdcl') << """
            settings {
              plugins [
                { id "java-ecosystem" },
                { id "checkstyle-ecosystem" }
              ]
              ${rootName ? "rootProject { name \"${rootName}\" }" : ""}
            }
        """
    }

    def "resolves the tool at the shipped default version and registers a task only where a source opts in"() {
        given:
        applyEcosystems()
        file('build.gradle.xdcl') << '''
            javaLibrary {
              checkstyle {}
              sources [
                {
                  name "main"
                  checkstyle {}
                },
                { name "test" }
              ]
            }
        '''

        when:
        succeeds("tasks", "--all")

        then: 'the javaLibrary-level checkstyle block resolved the shared classpath at the inline-default version'
        outputContains("checkstyle version=10.24.0")

        and: 'the main source set opted in, so its checkstyle task is registered'
        outputContains("checkMainCheckstyle")
        outputContains("checkstyle[main]")

        and: 'the test source set did not declare checkstyle { }, so the reaction skipped it (opt-in)'
        outputDoesNotContain("checkTestCheckstyle")
        outputDoesNotContain("checkstyle[test]")
    }

    def "runs checkstyle against real sources and produces the report"() {
        given:
        applyEcosystems("demo")
        file('config/checkstyle/checkstyle.xml') << '''<?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
              <module name="TreeWalker">
                <module name="FinalParameters"/>
              </module>
            </module>
        '''
        // In a NON-conventional directory, so the report only contains it if the reaction sourced from
        // the declared javaDirs (below) rather than a name-derived src/main/java.
        // No method parameters, so the FinalParameters check passes -> the task succeeds with no violations.
        file('custom-src/com/example/Ok.java') << '''
            package com.example;
            public class Ok {
                public String greeting() {
                    return "hello";
                }
            }
        '''
        file('build.gradle.xdcl') << """
            javaLibrary {
              repositories [ "${RepoScriptBlockUtil.mavenCentralMirrorUrl}" ]
              checkstyle {}
              sources [
                {
                  name "main"
                  javaDirs [ "custom-src" ]
                  checkstyle {}
                },
              ]
            }
        """

        when: 'the checkstyle task runs (resolving the tool classpath and analysing the main sources)'
        succeeds("checkMainCheckstyle")

        then: 'it executed and produced the XML report the reaction wired up'
        executedAndNotSkipped(":checkMainCheckstyle")
        file("build/reports/checkstyle/main.xml").assertExists()
        file("build/reports/checkstyle/main.xml").text.contains("com/example/Ok.java")
    }

    def "honors a custom configFile and ignoreFailures keeps the build green despite a violation"() {
        given:
        applyEcosystems("demo")
        file('cfg/my-checkstyle.xml') << '''<?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
              <property name="severity" value="error"/>
              <module name="TreeWalker">
                <module name="FinalParameters"/>
              </module>
            </module>
        '''
        // A non-final parameter violates FinalParameters (an error) -> the task would fail, but
        // ignoreFailures keeps the build green while still writing the report.
        file('custom-src/com/example/Bad.java') << '''
            package com.example;
            public class Bad {
                public String shout(String message) {
                    return message.toUpperCase();
                }
            }
        '''
        file('build.gradle.xdcl') << """
            javaLibrary {
              repositories [ "${RepoScriptBlockUtil.mavenCentralMirrorUrl}" ]
              checkstyle {}
              sources [
                {
                  name "main"
                  javaDirs [ "custom-src" ]
                  checkstyle {
                    configFile "cfg/my-checkstyle.xml"
                    ignoreFailures true
                  }
                },
              ]
            }
        """

        when: 'checkstyle runs against the violating source with ignoreFailures enabled'
        succeeds("checkMainCheckstyle")

        then: 'the build succeeds despite the violation, and the report (from the custom config) records it'
        executedAndNotSkipped(":checkMainCheckstyle")
        def report = file("build/reports/checkstyle/main.xml")
        report.assertExists()
        report.text.contains("Bad.java")
        report.text.contains("FinalParameters")
    }

    def "fails with a clear error when a source opts into checkstyle but the javaLibrary declares no checkstyle block"() {
        given:
        applyEcosystems("demo")
        // A valid config + source so the task is not NO-SOURCE and the only failing input is the
        // (unresolvable) tool classpath the missing javaLibrary-level block would have published.
        file('config/checkstyle/checkstyle.xml') << '''<?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
              <module name="TreeWalker">
                <module name="FinalParameters"/>
              </module>
            </module>
        '''
        file('custom-src/com/example/Ok.java') << '''
            package com.example;
            public class Ok {
                public String greeting() {
                    return "hello";
                }
            }
        '''
        file('build.gradle.xdcl') << """
            javaLibrary {
              repositories [ "${RepoScriptBlockUtil.mavenCentralMirrorUrl}" ]
              sources [
                {
                  name "main"
                  javaDirs [ "custom-src" ]
                  checkstyle {}
                },
              ]
            }
        """

        expect: 'the task fails resolving its classpath, with a message pointing at the missing block'
        fails("checkMainCheckstyle")
        failure.assertHasCause("Source set 'main' opted into checkstyle, but the javaLibrary declares no checkstyle { } block to set the Checkstyle version.")
    }
}
