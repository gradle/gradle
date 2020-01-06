/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class JavaExecIntegrationTest extends AbstractIntegrationSpec {

    TestFile mainJavaFile

    def setup() {
        mainJavaFile = file('src/main/java/Driver.java')
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

    @ToBeFixedForInstantExecution
    def "java exec is not incremental by default"() {
        when:
        run "run"

        then:
        executedAndNotSkipped ":run"

        when:
        run "run"

        then:
        executedAndNotSkipped ":run"
    }

    @ToBeFixedForInstantExecution
    def 'arguments passed via command line take precedence and is not incremental by default'() {
        when:
        run("run", "--args", "2 '3' \"4\"")

        then:
        executedAndNotSkipped ":run"
        assertOutputFileIs('''\
        2
        3
        4
        '''.stripIndent())

        when:
        run("run", "--args", "2 '3' \"4\"")

        then:
        executedAndNotSkipped ":run"
        assertOutputFileIs('''\
        2
        3
        4
        '''.stripIndent())
    }

    @Issue(["GRADLE-1483", "GRADLE-3528"])
    @ToBeFixedForInstantExecution
    def "when the user declares outputs it becomes incremental"() {
        given:
        buildFile << """
            run.outputs.file "out.txt"
        """

        when:
        run "run"

        then:
        executedAndNotSkipped ":run"

        when:
        run "run"

        then:
        skipped ":run"

        when:
        file("out.txt").delete()

        and:
        run "run"

        then:
        executedAndNotSkipped ":run"
    }

    @ToBeFixedForInstantExecution
    def 'arguments passed via command line matter in incremental check'() {
        given:
        buildFile << """
            run.outputs.file "out.txt"
        """

        when:
        run("run", "--args", "2")

        then:
        executedAndNotSkipped ":run"
        assertOutputFileIs("2\n")

        when:
        run("run", "--args", "2")

        then:
        skipped ":run"

        when:
        file("out.txt").delete()

        and:
        run("run", "--args", "2")

        then:
        executedAndNotSkipped ":run"
        assertOutputFileIs("2\n")
    }

    @ToBeFixedForInstantExecution
    def "arguments can be passed by using argument providers"() {
        given:
        def inputFile = file("input.txt")
        def outputFile = file("out.txt")
        buildFile << """
            class MyApplicationJvmArguments implements CommandLineArgumentProvider {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile
            
                @Override
                Iterable<String> asArguments() {
                    return ["-Dinput.file=\${inputFile.absolutePath}".toString()]
                }            
            }
            
            class MyApplicationCommandLineArguments implements CommandLineArgumentProvider {
                @OutputFile
                File outputFile

                @Override
                Iterable<String> asArguments() {
                    return [outputFile.absolutePath]
                }            
            }
            
            run.jvmArgumentProviders << new MyApplicationJvmArguments(inputFile: new File(project.property('inputFile')))
            
            run.argumentProviders << new MyApplicationCommandLineArguments(outputFile: new File(project.property('outputFile')))
             
        """
        inputFile.text = "first"
        mainJavaFile.text = mainClass("""
            try {
                String location = System.getProperty("input.file");
                BufferedReader reader = new BufferedReader(new FileReader(location));
                String input = reader.readLine();
                reader.close();
                FileWriter out = new FileWriter(args[args.length - 1], false);
                out.write(input);
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            
        """)

        when:
        run "run", "-PinputFile=${inputFile.absolutePath}", "-PoutputFile=${outputFile.absolutePath}"
        then:
        executedAndNotSkipped ":run"

        when:
        def secondInputFile = file("second-input.txt")
        secondInputFile.text = inputFile.text
        run "run", "-PinputFile=${secondInputFile.absolutePath}", "-PoutputFile=${outputFile.absolutePath}"
        then:
        outputFile.text == "first"
        skipped ":run"

        when:
        secondInputFile.text = "different"
        run "run", "-PinputFile=${secondInputFile.absolutePath}", "-PoutputFile=${outputFile.absolutePath}"
        then:
        executedAndNotSkipped ":run"
        outputFile.text == "different"
    }

    private void assertOutputFileIs(String text) {
        assert file("out.txt").text == text
    }
}
