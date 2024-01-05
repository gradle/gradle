/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.configurationcache


import org.gradle.internal.jvm.Jvm

import javax.annotation.Nullable

class ConfigurationCacheStandardStreamsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "#task task can run with default configuration"() {
        setUpProject(
            "${task}Task"(extraArg: "Gradle"))

        when:
        configurationCacheRun("run")

        then:
        outputContains("Hello, Gradle")

        where:
        task       || _
        "exec"     || _
        "javaExec" || _
    }

    def "#task task can redirect #source into #targetStream"() {
        setUpProject(
            "${task}Task"((source): targetStream, extraArg: "Gradle"),
            sourceStream)

        when:
        configurationCacheRun("run")

        then:
        ((targetStream == "System.out") ? output : errorOutput).contains("Hello, Gradle")

        where:
        task       | source           | sourceStream | targetStream
        "exec"     | "standardOutput" | "System.out" | "System.out"
        "exec"     | "standardOutput" | "System.out" | "System.err"
        "exec"     | "errorOutput"    | "System.err" | "System.out"
        "exec"     | "errorOutput"    | "System.err" | "System.err"
        "javaExec" | "standardOutput" | "System.out" | "System.out"
        "javaExec" | "standardOutput" | "System.out" | "System.err"
        "javaExec" | "errorOutput"    | "System.err" | "System.out"
        "javaExec" | "errorOutput"    | "System.err" | "System.err"
    }

    def "#task task can redirect System_in into standardInput"() {
        setUpProject("${task}Task"(standardInput: "System.in"))
        withStdInContents("Gradle")

        when:
        configurationCacheRun("run")

        then:
        outputContains("Hello, Gradle")

        where:
        task       || _
        "exec"     || _
        "javaExec" || _
    }

    def "#task task cannot use #targetStreamType as #source"() {
        setUpProject(
            "${task}Task"((source): targetStream, extraArg: "Gradle"),
            sourceStream)

        when:
        configurationCacheFails("run")

        then:
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 1
            withProblem("Task `:run` of type `org.gradle.api.tasks.${task.capitalize()}`: cannot serialize object of type '$targetStreamType', a subtype of 'java.io.OutputStream', " +
                "as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }

        where:
        task       | source           | sourceStream | targetStreamType                | targetStream
        "exec"     | "standardOutput" | "System.out" | "java.io.ByteArrayOutputStream" | "new ByteArrayOutputStream()"
        "exec"     | "errorOutput"    | "System.err" | "java.io.ByteArrayOutputStream" | "new ByteArrayOutputStream()"
        "exec"     | "standardOutput" | "System.out" | "java.io.FileOutputStream"      | "new FileOutputStream(file('output.txt'))"
        "exec"     | "errorOutput"    | "System.err" | "java.io.FileOutputStream"      | "new FileOutputStream(file('output.txt'))"
        "javaExec" | "standardOutput" | "System.out" | "java.io.ByteArrayOutputStream" | "new ByteArrayOutputStream()"
        "javaExec" | "errorOutput"    | "System.err" | "java.io.ByteArrayOutputStream" | "new ByteArrayOutputStream()"
        "javaExec" | "standardOutput" | "System.out" | "java.io.FileOutputStream"      | "new FileOutputStream(file('output.txt'))"
        "javaExec" | "errorOutput"    | "System.err" | "java.io.FileOutputStream"      | "new FileOutputStream(file('output.txt'))"
    }

    def "#task task cannot use #sourceStreamType as standardInput"() {
        setUpProject(
            "${task}Task"(standardInput: sourceStream))

        buildFile """
            file("input.txt").text = "Gradle"
        """

        when:
        configurationCacheFails("run")

        then:
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 1
            withProblem("Task `:run` of type `org.gradle.api.tasks.${task.capitalize()}`: cannot serialize object of type '$sourceStreamType', a subtype of 'java.io.InputStream', " +
                "as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }

        where:
        task       | sourceStreamType               | sourceStream
        "exec"     | "java.io.ByteArrayInputStream" | "new ByteArrayInputStream('Gradle'.getBytes('UTF-8'))"
        "exec"     | "java.io.FileInputStream"      | "new FileInputStream(file('input.txt'))"
        "javaExec" | "java.io.ByteArrayInputStream" | "new ByteArrayInputStream('Gradle'.getBytes('UTF-8'))"
        "javaExec" | "java.io.FileInputStream"      | "new FileInputStream(file('input.txt'))"
    }

    def setUpProject(String task, String outputStream = "System.out") {
        buildFile """
        plugins {
            id("java")
        }

        $task
        """

        file("src/main/java/Main.java") << """
            import java.io.*;

            public class Main {
                public static void main(String[] args) throws IOException {
                    String name;
                    if (args.length < 1) {
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
                            name = in.readLine();
                            if (name != null) {
                                name = name.trim();
                            }
                        }
                    } else {
                        name = args[0];
                    }
                    ${outputStream}.println("Hello, " + name);
                }
            }
        """
    }

    def withStdInContents(String message) {
        executer.withStdinPipe(new PipedOutputStream() {
            @Override
            synchronized void connect(PipedInputStream snk) throws IOException {
                super.connect(snk)
                write(message.bytes)
                close()
            }
        })
    }

    @SuppressWarnings('unused')
    // called by string
    def execTask(Map<String, String> args) {
        return """
            tasks.register("run", Exec) {
                dependsOn(compileJava)
                executable = ${Jvm.canonicalName}.current().javaExecutable
                args '-cp', project.layout.files(compileJava).asPath, 'Main' ${formatArgument args["extraArg"], { ", '$it'" }}
                ${formatArgument args["standardInput"], { "standardInput = $it" }}
                ${formatArgument args["standardOutput"], { "standardOutput = $it" }}
                ${formatArgument args["errorOutput"], { "errorOutput = $it" }}
            }
        """
    }

    @SuppressWarnings('unused')
    // called by string
    def javaExecTask(Map<String, String> args) {
        return """
            tasks.register("run", JavaExec) {
                classpath = project.layout.files(compileJava)
                mainClass = "Main"
                ${formatArgument args["standardInput"], { "standardInput = $it" }}
                ${formatArgument args["standardOutput"], { "standardOutput = $it" }}
                ${formatArgument args["errorOutput"], { "errorOutput = $it" }}
                ${formatArgument args["extraArg"], { "args '$it'" }}
            }
        """
    }

    private def formatArgument(@Nullable argument, Closure<String> resultBuilder) {
        argument?.with(resultBuilder) ?: ""
    }
}
