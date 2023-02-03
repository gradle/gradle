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

import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile

class ExecutionResultExecTaskIntegrationTest extends AbstractExecutionResultExecTaskIntegrationTest {
    TestFile mainJavaFile

    @Override
    protected void makeExecProject() {
        buildFile.text = """
            apply plugin: "java"

            task run(type: Exec) {
                dependsOn(compileJava)
                executable = ${Jvm.canonicalName}.current().javaExecutable
                args '-cp', project.layout.files(compileJava).asPath, 'driver.Driver', "1"
            }
        """
    }

    @Override
    protected void writeSucceedingExec() {
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
    }

    @Override
    protected void writeFailingExec() {
        mainJavaFile = file('src/main/java/Driver.java')
        file("src/main/java/Driver.java").text = mainClass("""
            System.exit(42);
        """)
    }

    @Override
    protected String getTaskNameUnderTest() {
        return "run"
    }

    private static String mainClass(String body) {
        return """
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

    @Override
    protected String getExecResultDsl() {
        return "${taskUnderTestDsl}.executionResult.getOrNull()"
    }
}
