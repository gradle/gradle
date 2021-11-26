/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import org.gradle.process.ShellScript
import org.gradle.process.TestJavaMain
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

/**
 * Helper to test {@code exec} and {@code javaexec} methods on scripts and {@code ExecOperations}.
 */
class ExecOperationsFixture {
    private final TestFile testDirectory
    private final ShellScript testExecutable = ShellScript.builder()
        .printText("Hello from script")
        .writeTo(testDirectory, "test")

    ExecOperationsFixture(TestFile testDirectory) {
        this.testDirectory = testDirectory
    }

    private String getTestCommandLine() {
        return testExecutable.commandLine.collect { """ "${TextUtil.escapeString(it)}" """ }.join(", ")
    }

    abstract class ExecFormatter {
        abstract String execSpec()

        abstract String javaexecSpec()

        abstract String callProcessAndPrintOutput(String method, String spec)

        String imports() {
            return ""
        }
    }

    abstract class UniversalFormatter extends ExecFormatter {
        @Override
        String execSpec() {
            return """
                commandLine($testCommandLine)
                setStandardOutput(baos)
            """
        }

        @Override
        String javaexecSpec() {
            return """
                mainClass.set("${TestJavaMain.class.name}")
                classpath("${TextUtil.escapeString(TestJavaMain.classLocation)}")
                args("Hello", "from", "Java")
                setStandardOutput(baos)
            """
        }
    }

    ExecFormatter groovyFormatter() {
        return new UniversalFormatter() {
            @Override
            String callProcessAndPrintOutput(String method, String spec) {
                return """
                ByteArrayOutputStream baos = new ByteArrayOutputStream()
                $method {
                    $spec
                }
                println(baos.toString())
            """
            }
        }
    }

    ExecFormatter javaFormatter() {
        return new ExecFormatter() {
            @Override
            String execSpec() {
                return """
                it.commandLine($testCommandLine);
                it.setStandardOutput(baos);
            """
            }

            @Override
            String javaexecSpec() {
                return """
                it.getMainClass().set("${TestJavaMain.class.name}");
                it.classpath("${TextUtil.escapeString(TestJavaMain.classLocation)}");
                it.args("Hello", "from", "Java");
                it.setStandardOutput(baos);
            """
            }

            @Override
            String callProcessAndPrintOutput(String method, String spec) {
                return """
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    $method(it -> {
                        $spec
                    });
                    System.out.println(baos.toString());
                """
            }

            @Override
            String imports() {
                return "import ${ByteArrayOutputStream.name};"
            }
        }
    }

    ExecFormatter kotlinFormatter() {
        return new UniversalFormatter() {
            @Override
            String callProcessAndPrintOutput(String method, String spec) {
                return """
                    val baos = ByteArrayOutputStream()
                    $method {
                        $spec
                    }
                    println(baos.toString())
                """
            }

            @Override
            String imports() {
                return "import ${ByteArrayOutputStream.name}"
            }
        }
    }
}
