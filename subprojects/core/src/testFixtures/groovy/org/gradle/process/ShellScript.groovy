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
package org.gradle.process

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

import java.nio.file.Path

/**
 * The cross-platform builder of shell scripts that can be used to verify correctness of the
 * invocation of an executable. It uses "sh" syntax on POSIX platforms and batch files on Windows.
 */
abstract class ShellScript {
    protected final TestFile scriptFile

    protected ShellScript(TestFile scriptFile) {
        this.scriptFile = scriptFile
    }

    /**
     * Returns a command line elements that can be used to start this script.
     * The resulting list can be used as an argument for the {@link ProcessBuilder} or Groovy's {@code execute} method.
     * The first element of the list is the command interpreter (sh or cmd depending on the platform).
     * The list also contains an absolute path to the script (which may contain spaces).
     *
     * @return the list of command line elements to start this script.
     */
    abstract List<String> getCommandLine();

    /**
     * Returns a command line elements that can be used to start this script.
     * The resulting list can be used as an argument for the {@link ProcessBuilder} or Groovy's {@code execute} method.
     * The first element of the list is the command interpreter (sh or cmd depending on the platform).
     * The list also contains a path to the script relative to {@code baseDir}.
     * This means that {@code baseDir} has to be used as a current dir when executing the command,
     * otherwise interpreter won't be able to find the script file.
     *
     * @param baseDir the directory to which the script path is relative
     *
     * @return the list of command line elements to start this script.
     */
    abstract List<String> getRelativeCommandLine(File baseDir);

    protected String getRelativePath(File baseDir) {
        // Do not use Groovy's baseDir.relativePath there. It has a custom implementation
        // and assumes that having "/" as a separator in Windows path is fine. It isn't.
        Path basePath = baseDir.absoluteFile.toPath()
        Path scriptPath = scriptFile.absoluteFile.toPath()

        return basePath.relativize(scriptPath).toString()
    }

    private static class WindowsScript extends ShellScript {
        private WindowsScript(TestFile scriptFile) {
            super(scriptFile)
        }

        @Override
        List<String> getCommandLine() {
            return ["cmd.exe", "/d", "/c", scriptFile.absolutePath]
        }

        @Override
        List<String> getRelativeCommandLine(File baseDir) {
            return ["cmd.exe", "/d", "/c", getRelativePath(baseDir)]
        }
    }


    private static class PosixScript extends ShellScript {
        private PosixScript(TestFile scriptFile) {
            super(scriptFile)
        }

        @Override
        List<String> getCommandLine() {
            return ["/bin/sh", scriptFile.absolutePath]
        }

        @Override
        List<String> getRelativeCommandLine(File baseDir) {
            return ["/bin/sh", getRelativePath(baseDir)]
        }
    }

    static abstract class Builder {
        private final StringBuilder scriptCommands = new StringBuilder()

        protected Builder() {}

        abstract Builder printArguments();

        abstract Builder printText(String text);

        abstract Builder printEnvironmentVariable(String variableName)

        abstract Builder printWorkingDir()

        abstract Builder withExitValue(int exitValue)

        protected Builder addLine(String line) {
            scriptCommands.append(line).append('\n')
            return this
        }

        ShellScript writeTo(TestFile baseDir, String basename) {
            def scriptFile = baseDir.file(basename + scriptExtension)
            scriptFile.write(scriptCommands.toString())
            return build(scriptFile)
        }

        protected abstract ShellScript build(TestFile scriptFile)

        protected abstract String getScriptExtension();
    }

    private static class PosixBuilder extends Builder {
        @Override
        protected ShellScript build(TestFile scriptFile) {
            return new PosixScript(scriptFile)
        }

        @Override
        Builder printArguments() {
            return addLine('echo "$*"')
        }

        @Override
        Builder printText(String text) {
            return addLine("echo '$text'")
        }

        @Override
        Builder printEnvironmentVariable(String variableName) {
            return addLine("echo $variableName=\$$variableName")
        }

        @Override
        Builder printWorkingDir() {
            return addLine("echo CWD=\$(pwd)")
        }

        @Override
        Builder withExitValue(int exitValue) {
            return addLine("exit $exitValue")
        }

        @Override
        protected String getScriptExtension() {
            return ".sh"
        }
    }

    private static class WindowsBuilder extends Builder {
        WindowsBuilder() {
            addLine("@echo off")
        }

        @Override
        Builder printArguments() {
            return addLine("echo %*")
        }

        @Override
        Builder printText(String text) {
            return addLine("echo $text")
        }

        @Override
        Builder printEnvironmentVariable(String variableName) {
            return addLine("echo $variableName=%$variableName%")
        }

        @Override
        Builder printWorkingDir() {
            return addLine("echo CWD=%CD%")
        }

        @Override
        Builder withExitValue(int exitValue) {
            return addLine("exit $exitValue")
        }

        @Override
        protected ShellScript build(TestFile scriptFile) {
            return new WindowsScript(scriptFile)
        }

        @Override
        protected String getScriptExtension() {
            return ".bat"
        }
    }

    static Builder builder() {
        if (OperatingSystem.current().windows) {
            return new WindowsBuilder()
        }
        return new PosixBuilder()
    }

    /**
     * Converts a list of command line elements (returned by {@link #getCommandLine()}) to a list of Java/Groovy/Kotlin string literals.
     * Literals include surrounding quotes and have special symbols escaped, so they are safe to use in the sources.
     * @param cmd the command line elements to be converted
     * @return a List of string literals
     */
    static List<String> cmdToStringLiterals(List<String> cmd) {
        return cmd.collect { "\"${TextUtil.escapeString(it)}\"".toString() }
    }

    /**
     * Converts a list of command line elements (returned by {@link #getCommandLine()}) to a comma-separated list of Java/Groovy/Kotlin string literals.
     * String literals include surrounding quotes and have special symbols escaped.
     * The returned string can be used to generate call to a method that accepts varargs.
     *
     * @param cmd the command line elements to be converted
     * @return a comma-separated list of string literals
     */
    static String cmdToVarargLiterals(List<String> cmd) {
        return cmdToStringLiterals(cmd).join(", ")
    }

    /**
     * Converts a list of command line elements (returned by {@link #getCommandLine()}) to a single Java/Groovy/Kotlin string literal.
     * String literal includes surrounding quotes and has special symbols escaped.
     * This method throws {@code IllegalArgumentException} if any of {@code cmd} elements contain spaces.
     *
     * @param cmd the command line elements to be converted
     * @return a comma-separated list of string literals
     */
    static String cmdToStringLiteral(List<String> cmd) {
        if (cmd.any { it.contains(" ") }) {
            throw new IllegalArgumentException("There is an element with space in $cmd")
        }
        return "\"${TextUtil.escapeString(cmd.join(" "))}\""
    }
}
