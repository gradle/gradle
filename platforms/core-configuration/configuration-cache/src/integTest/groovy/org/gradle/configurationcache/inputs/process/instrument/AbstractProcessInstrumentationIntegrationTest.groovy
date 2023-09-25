/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.inputs.process.instrument

import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest
import org.gradle.process.ShellScript

/**
 * Base class for all external process invocation instrumentation tests.
 */
abstract class AbstractProcessInstrumentationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    // Note that all tests use a relative path to the script because its absolute path may contain
    // spaces and it breaks logic String.execute which splits the given string at spaces without
    // any options to escape the space.
    ShellScript baseScript = ShellScript.builder().printEnvironmentVariable('FOOBAR').printWorkingDir().writeTo(testDirectory, "test")

    def setup() {
        testDirectory.createDir(pwd)
    }


    static String getPwd() {
        return "tmp"
    }

    abstract static class VarInitializer {
        final String description

        VarInitializer(String description) {
            this.description = description
        }

        String getGroovy(List<String> cmd) {
            throw new UnsupportedOperationException()
        }

        String getJava(List<String> cmd) {
            throw new UnsupportedOperationException()
        }

        String getKotlin(List<String> cmd) {
            throw new UnsupportedOperationException()
        }

        @Override
        String toString() {
            return description
        }
    }

    static VarInitializer fromString() {
        return new VarInitializer("String") {
            @Override
            String getGroovy(List<String> cmd) {
                return """String command = ${ShellScript.cmdToStringLiteral(cmd)} """
            }

            @Override
            String getJava(List<String> cmd) {
                return """String command = ${ShellScript.cmdToStringLiteral(cmd)};"""
            }

            @Override
            String getKotlin(List<String> cmd) {
                return """val command = ${ShellScript.cmdToStringLiteral(cmd)} """
            }
        }
    }

    static VarInitializer fromGroovyString() {
        return new VarInitializer("GString") {
            @Override
            String getGroovy(List<String> cmd) {
                return """
                        String rawCommand = ${ShellScript.cmdToStringLiteral(cmd)}
                        def command = "\${rawCommand.toString()}"
                    """
            }
        }
    }

    static VarInitializer fromStringArray() {
        return new VarInitializer("String[]") {
            @Override
            String getGroovy(List<String> cmd) {
                return """String[] command = [${ShellScript.cmdToVarargLiterals(cmd)}]"""
            }

            @Override
            String getJava(List<String> cmd) {
                return """String[] command = new String[] { ${ShellScript.cmdToVarargLiterals(cmd)} };"""
            }

            @Override
            String getKotlin(List<String> cmd) {
                return """val command = arrayOf(${ShellScript.cmdToVarargLiterals(cmd)}) """
            }
        }
    }

    static VarInitializer fromStringList() {
        return new VarInitializer("List<String>") {
            @Override
            String getGroovy(List<String> cmd) {
                return """
                        def command = [${ShellScript.cmdToVarargLiterals(cmd)}]
                    """
            }

            @Override
            String getJava(List<String> cmd) {
                return """
                    List<String> command = Arrays.asList(${ShellScript.cmdToVarargLiterals(cmd)});
                """
            }

            @Override
            String getKotlin(List<String> cmd) {
                return """
                    val command = listOf(${ShellScript.cmdToVarargLiterals(cmd)})
                """
            }
        }
    }

    static VarInitializer fromObjectList() {
        return new VarInitializer("List<Object>") {
            @Override
            String getGroovy(List<String> cmd) {
                return """
                    def someArgument = '--some-argument'
                    def command = [${ShellScript.cmdToVarargLiterals(cmd)}, "\${someArgument.toString()}"]
                    """
            }

            @Override
            String getJava(List<String> cmd) {
                return """
                    Object someArgument = new Object() {
                        public String toString() {
                            return "--some-argument";
                        }
                    };
                    List<Object> command = Arrays.<Object>asList(${ShellScript.cmdToVarargLiterals(cmd)}, someArgument);
                    """
            }

            @Override
            String getKotlin(List<String> cmd) {
                return """
                    val someArgument = object : Any() {
                        override fun toString(): String = "--some-argument"
                    }
                    val command = listOf<Any>(${ShellScript.cmdToVarargLiterals(cmd)}, someArgument)
                    """
            }
        }
    }

    void generateClassesWithClashingMethods() {
        def sourceFolder = testDirectory.createDir("buildSrc/src/main/java")

        sourceFolder.file("ProcessGroovyMethodsExecute.java") << """
            import java.io.*;
            import java.util.*;

            public class ProcessGroovyMethodsExecute {

                public static Process execute(String command) { return null; }
                public static Process execute(String command, String[] envp, File file) { return null; }
                public static Process execute(String command, List<?> envp, File file) { return null; }
                public static Process execute(String[] command) { return null; }
                public static Process execute(String[] command, String[] envp, File file) { return null; }
                public static Process execute(String[] command, List<?> envp, File file) { return null; }
                public static Process execute(List<?> command) { return null; }
                public static Process execute(List<?> command, String[] envp, File file) { return null; }
                public static Process execute(List<?> command, List<?> envp, File file) { return null; }
            }
        """

        sourceFolder.file("RuntimeExec.java") << """
            import java.io.*;
            import java.util.*;

            public class RuntimeExec {
                public Process exec(String command) { return null; }
                public Process exec(String command, String[] envp) { return null; }
                public Process exec(String command, String[] envp, File file) { return null; }
                public Process exec(String command, List<?> envp, File file) { return null; }
                public Process exec(String[] command) { return null; }
                public Process exec(String[] command, String[] envp) { return null; }
                public Process exec(String[] command, String[] envp, File file) { return null; }
            }
        """

        sourceFolder.file("ProcessBuilderStart.java") << """
            public class ProcessBuilderStart {
                public Process start() { return null; }
            }
        """
    }
}
