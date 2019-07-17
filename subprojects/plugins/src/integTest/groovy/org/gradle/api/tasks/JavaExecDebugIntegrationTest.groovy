/*
 * Copyright 2019 the original author or authors.
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


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.junit.Rule

class JavaExecDebugIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    JDWPUtil jdwpClient = new JDWPUtil()

    def "Debug is disabled by default"() {
        setup:
        sampleProject"""
            debugOptions {
            }
        """

        expect:
        succeeds('run')
    }

    def "Deebug mode can be disabled"() {
        setup:
        sampleProject"""
            debugOptions {
                enabled = false
            }
        """

        expect:
        succeeds('run')
    }

    def "Debug session fails without debugger"() {
        setup:
        sampleProject"""
            debugOptions {
                enabled = true
            }
        """

        expect:
        def failure = fails('run')
        failure.assertHasErrorOutput('ERROR: transport error 202: connect failed: Connection refused')
    }

    def "Can debug Java exec"() {
        setup:
        sampleProject"""    
            debugOptions {
                enabled = true
                server = false
                suspend = false
                port = $jdwpClient.port
            }
        """
        jdwpClient.listen()

        expect:
        succeeds('run')
    }

    def "Debug options overrides debug property"() {
        setup:
        sampleProject"""    
            debug = true
        
            debugOptions {
                enabled = false
            }
        """

        expect:
        succeeds('run')
    }

    def "If custom debug argument is passed to the build then debug options is ignored"() {
        setup:
        sampleProject"""    
            debugOptions {
                enabled = true
                server = false
                suspend = false
            }
            
            jvmArgs "-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=$jdwpClient.port"
        """

        jdwpClient.listen()

        expect:
        succeeds('run')
        output.contains "Debug configuration ignored in favor of the supplied JVM arguments: [-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=$jdwpClient.port]"
    }


    private void sampleProject(String runConfig) {
        file('src/main/java/Driver.java')
        file("src/main/java/Driver.java").text = mainClass("""
            try {
                FileWriter out = new FileWriter("out.txt");
                for (String arg: args) {
                    out.write(arg);
                    out.write("\\n");
                }
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        """)


        buildFile.text = """
            apply plugin: "java"

            task run(type: JavaExec) {
                classpath = project.layout.files(compileJava)
                main "driver.Driver"
                args "1"
                
                $runConfig
            }
        """
    }

    private static String mainClass(String body) {
        """
            package driver;

            import java.io.*;
            import java.lang.System;

            public class Driver {
                public static void main(String[] args) {
                ${body}
                }
            }
        """
    }
}
