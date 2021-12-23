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
import org.gradle.test.fixtures.file.TestFile;

/**
 * The cross-platform builder of shell scripts that can be used to verify correctness of the
 * invocation of an executable. It uses "sh" syntax on POSIX platforms and batch files on Windows.
 */
abstract class ShellScript {
    protected final TestFile scriptFile

    protected ShellScript(TestFile scriptFile) {
        this.scriptFile = scriptFile
    }

    List<String> getCommandLine() {
        return getCommandLineWithArguments()
    }

    abstract List<String> getCommandLineWithArguments(String... args)

    private static class WindowsScript extends ShellScript {
        private WindowsScript(TestFile scriptFile) {
            super(scriptFile)
        }

        List<String> getCommandLineWithArguments(String... args) {
            return [scriptFile.absolutePath] + args.toList()
        }
    }

    private static class PosixScript extends ShellScript {
        private PosixScript(TestFile scriptFile) {
            super(scriptFile)
        }

        List<String> getCommandLineWithArguments(String... args) {
            return ["/bin/sh", scriptFile.absolutePath] + args.toList()
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
            return addLine("echo \"$text\"")
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

}
