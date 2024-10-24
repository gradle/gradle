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

package org.gradle.internal.cc.impl.fixtures

import org.codehaus.groovy.runtime.ProcessGroovyMethods
import org.gradle.process.ShellScript
import org.gradle.process.TestJavaMain
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

import javax.annotation.Nullable
import java.util.function.Function

/**
 * Helper to test {@code exec} and {@code javaexec} methods on scripts and {@code ExecOperations}.
 */
class ExternalProcessFixture {
    private final TestFile testDirectory
    private final ShellScript testExecutable = ShellScript.builder()
        .printText("Hello from script")
        .writeTo(testDirectory, "test")

    ExternalProcessFixture(TestFile testDirectory) {
        this.testDirectory = testDirectory
    }

    private String getCommandLineAsVarargLiterals() {
        return ShellScript.cmdToVarargLiterals(testExecutable.commandLine)
    }

    interface Snippets {
        abstract String getBody()

        abstract String getImports()
    }

    interface SnippetsFactory {
        abstract Snippets newSnippets(ExternalProcessFixture fixture)

        abstract String getSummary()
    }

    interface PrintProcessOutput {
        abstract SnippetsFactory getGroovy()

        abstract SnippetsFactory getKotlin()

        abstract SnippetsFactory getJava()
    }


    static PrintProcessOutput exec() {
        return exec(null)
    }

    static PrintProcessOutput exec(String instance) {
        return new ExecJavaexecPrintOutput(instance, "exec", ExternalProcessFixture::getGroovyKotlinExecSpec, ExternalProcessFixture::getGroovyKotlinExecSpec, ExternalProcessFixture::getJavaExecSpec)
    }

    static PrintProcessOutput javaexec() {
        return javaexec(null)
    }

    static PrintProcessOutput javaexec(String instance) {
        return new ExecJavaexecPrintOutput(
            instance, "javaexec", ExternalProcessFixture::getGroovyKotlinJavaexecSpec, ExternalProcessFixture::getGroovyKotlinJavaexecSpec, ExternalProcessFixture::getJavaJavaexecSpec)
    }

    private static SnippetsFactory makeFactory(String summary, Function<ExternalProcessFixture, Snippets> factory) {
        return new SnippetsFactory() {
            @Override
            Snippets newSnippets(ExternalProcessFixture fixture) {
                return factory.apply(fixture)
            }

            @Override
            String getSummary() {
                return summary
            }
        }
    }

    private String getGroovyKotlinExecSpec() {
        return """
                commandLine($commandLineAsVarargLiterals)
                getStandardOutput().set(baos)
            """
    }

    private String getGroovyKotlinJavaexecSpec() {
        return """
                mainClass.set("${TestJavaMain.class.name}")
                classpath("${TextUtil.escapeString(TestJavaMain.classLocation)}")
                args("Hello", "from", "Java")
                getStandardOutput().set(baos)
            """
    }

    private String getJavaExecSpec() {
        return """
                it.commandLine($commandLineAsVarargLiterals);
                it.getStandardOutput().set(baos);
            """
    }

    private String getJavaJavaexecSpec() {
        return """
                it.getMainClass().set("${TestJavaMain.class.name}");
                it.classpath("${TextUtil.escapeString(TestJavaMain.classLocation)}");
                it.args("Hello", "from", "Java");
                it.getStandardOutput().set(baos);
            """
    }

    private Snippets newGroovyExecJavaexecSnippets(String method, String spec) {
        return newSnippets(
            """
                ByteArrayOutputStream baos = new ByteArrayOutputStream()
                $method {
                    $spec
                }
                println(baos.toString())
            """, "import ${ByteArrayOutputStream.name}")
    }

    private Snippets newKotlinExecJavaexecSnippets(String method, String spec) {
        return newSnippets(
            """
                val baos = ByteArrayOutputStream()
                $method {
                    $spec
                }
                println(baos.toString())
            """,
            "import ${ByteArrayOutputStream.name}")

    }

    private Snippets newJavaExecJavaexecSnippets(String method, String spec) {
        return newSnippets(
            """
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                $method(it -> {
                    $spec
                });
                System.out.println(baos.toString());
            """,
            "import ${ByteArrayOutputStream.name};")
    }

    private static class ExecJavaexecPrintOutput implements PrintProcessOutput {
        private final String method
        private final Function<ExternalProcessFixture, String> groovySpecFactory
        private final Function<ExternalProcessFixture, String> kotlinSpecFactory
        private final Function<ExternalProcessFixture, String> javaSpecFactory

        ExecJavaexecPrintOutput(
            @Nullable String instance,
            String methodName,
            Function<ExternalProcessFixture, String> groovySpecFactory,
            Function<ExternalProcessFixture, String> kotlinSpecFactory,
            Function<ExternalProcessFixture, String> javaSpecFactory
        ) {
            this.method = instance != null ? "${instance}.$methodName" : methodName
            this.groovySpecFactory = groovySpecFactory
            this.kotlinSpecFactory = kotlinSpecFactory
            this.javaSpecFactory = javaSpecFactory
        }

        @Override
        SnippetsFactory getGroovy() {
            return makeFactory(method) { fixture ->
                fixture.newGroovyExecJavaexecSnippets(method, groovySpecFactory.apply(fixture))
            }
        }

        @Override
        SnippetsFactory getKotlin() {
            return makeFactory(method) { fixture ->
                fixture.newKotlinExecJavaexecSnippets(method, kotlinSpecFactory.apply(fixture))
            }
        }

        @Override
        SnippetsFactory getJava() {
            return makeFactory(method) { fixture ->
                fixture.newJavaExecJavaexecSnippets(method, javaSpecFactory.apply(fixture))
            }
        }
    }

    private static Snippets newSnippets(String body, String imports) {
        return new Snippets() {
            @Override
            String getBody() {
                return body
            }

            @Override
            String getImports() {
                return imports
            }
        }
    }

    static PrintProcessOutput processBuilder() {
        return new ProcessApiPrintOutput("new ProcessBuilder(command).start()", "ProcessBuilder(*command).start()", "new ProcessBuilder(command).start()")
    }

    static PrintProcessOutput stringArrayExecute() {
        return new ProcessApiPrintOutput("command.execute()", "ProcessGroovyMethods.execute(command)", "ProcessGroovyMethods.execute(command)")
    }

    static PrintProcessOutput runtimeExec() {
        return new ProcessApiPrintOutput("Runtime.getRuntime().exec(command)")
    }


    private static class ProcessApiPrintOutput implements PrintProcessOutput {
        private final String makeProcessMethodGroovy
        private final String makeProcessMethodKotlin
        private final String makeProcessMethodJava

        ProcessApiPrintOutput(String makeProcessMethod) {
            this(makeProcessMethod, makeProcessMethod, makeProcessMethod)
        }

        ProcessApiPrintOutput(String makeProcessMethodGroovy, String makeProcessMethodKotlin, String makeProcessMethodJava) {
            this.makeProcessMethodGroovy = makeProcessMethodGroovy
            this.makeProcessMethodKotlin = makeProcessMethodKotlin
            this.makeProcessMethodJava = makeProcessMethodJava
        }

        @Override
        SnippetsFactory getGroovy() {
            return makeFactory(makeProcessMethodGroovy) { fixture ->
                newSnippets(
                    """
                        String[] command = [${fixture.commandLineAsVarargLiterals}]
                        ${makeProcessMethodGroovy}.waitForProcessOutput(System.out, System.err)
                    """,
                    "")
            }
        }

        @Override
        SnippetsFactory getKotlin() {
            return makeFactory(makeProcessMethodKotlin) { fixture ->
                newSnippets(
                    """
                        val command = arrayOf(${fixture.commandLineAsVarargLiterals})
                        ProcessGroovyMethods.waitForProcessOutput(${makeProcessMethodKotlin}, System.out as OutputStream, System.err as OutputStream)
                    """,
                    """
                        import ${OutputStream.name}
                        import ${ProcessGroovyMethods.name}
                    """)
            }
        }

        @Override
        SnippetsFactory getJava() {
            return makeFactory(makeProcessMethodJava) { fixture ->
                newSnippets(
                    """
                        try {
                            String[] command = new String[] { ${fixture.commandLineAsVarargLiterals} };
                            ProcessGroovyMethods.waitForProcessOutput(${makeProcessMethodJava}, (OutputStream) System.out, (OutputStream) System.err);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    """,
                    """
                        import ${IOException.name};
                        import ${OutputStream.name};
                        import ${ProcessGroovyMethods.name};
                        import ${UncheckedIOException.name};
                    """)
            }
        }
    }
}
