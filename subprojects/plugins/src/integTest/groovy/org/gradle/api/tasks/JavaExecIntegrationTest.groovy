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
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
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
                mainClass = "driver.Driver"
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

    def "emits deprecation warning if executable specified as relative path"() {
        given:
        def executable = TextUtil.normaliseFileSeparators(Jvm.current().javaExecutable.toString())

        buildFile << """
            tasks.withType(JavaExec) {
                executable = new File(".").getAbsoluteFile().toPath().relativize(new File("${executable}").toPath()).toString()
            }
        """

        when:
        executer.expectDeprecationWarning("Configuring a Java executable via a relative path. " +
                "This behavior has been deprecated. This will fail with an error in Gradle 9.0. " +
                "Resolving relative file paths might yield unexpected results, there is no single clear location it would make sense to resolve against. " +
                "Configure an absolute path to a Java executable instead.")
        run "run"

        then:
        executedAndNotSkipped ":run"
    }

    def "is not incremental by default"() {
        when:
        run "run"

        then:
        executedAndNotSkipped ":run"

        when:
        run "run"

        then:
        executedAndNotSkipped ":run"
    }

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

    def "arguments can be passed by using argument providers"() {
        given:
        def inputFile = file("input.txt")
        def outputFile = file("out.txt")
        buildFile << """
            abstract class MyApplicationJvmArguments implements CommandLineArgumentProvider {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                abstract RegularFileProperty getInputFile()

                @Override
                Iterable<String> asArguments() {
                    return ["-Dinput.file=\${inputFile.get().asFile.absolutePath}".toString()]
                }
            }

            abstract class MyApplicationCommandLineArguments implements CommandLineArgumentProvider {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @Override
                Iterable<String> asArguments() {
                    return [outputFile.get().asFile.absolutePath]
                }
            }

            def projectDir = layout.projectDirectory

            def input = providers.gradleProperty('inputFile').map {
                projectDir.file(it)
            }
            run.jvmArgumentProviders << objects.newInstance(MyApplicationJvmArguments).tap {
                it.inputFile.set(input)
            }

            def output = providers.gradleProperty('outputFile').map {
                projectDir.file(it)
            }
            run.argumentProviders << objects.newInstance(MyApplicationCommandLineArguments).tap {
                it.outputFile.set(output)
            }

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

    @Issue("https://github.com/gradle/gradle/issues/12832")
    def "classpath can be replaced with a file collection including the replaced value"() {
        given:
        buildFile.text = """
            apply plugin: "java"

            task run(type: JavaExec) {
                classpath = project.layout.files(compileJava)
                classpath = files(classpath, "someOtherFile.jar").filter(Specs.SATISFIES_ALL)
                mainClass = "driver.Driver"
            }
        """

        when:
        run "run"

        then:
        executedAndNotSkipped ":run"
    }

    private void assertOutputFileIs(String text) {
        assert file("out.txt").text == text
    }
}
