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

package org.gradle.process;

import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

import java.util.List;

class ExecSpecAdapters {

    /**
     * Adapter for {@link ExecSpec#getCommandLine()}}
     *
     * Note: Getter is upgraded via {@link BaseExecSpec#getCommandLine()} upgrade.
     */
    static class CommandLineAdapter {
        @BytecodeUpgrade
        static void setCommandLine(ExecSpec self, List<String> args) {
            self.commandLine(args);
        }

        @BytecodeUpgrade
        static void setCommandLine(ExecSpec self, Object... args) {
            self.commandLine(args);
        }

        @BytecodeUpgrade
        static void setCommandLine(ExecSpec self, Iterable<?> args) {
            self.commandLine(args);
        }
    }

    /**
     * Adapter for {@link ExecSpec#getArgs()}}
     */
    static class ArgsAdapter {

        @BytecodeUpgrade
        static List<String> getArgs(ExecSpec self) {
            return self.getArgs().get();
        }

        @BytecodeUpgrade
        static ExecSpec setArgs(ExecSpec self, List<String> args) {
            self.getArgs().empty();
            self.args(args);
            return self;
        }

        @BytecodeUpgrade
        static ExecSpec setArgs(ExecSpec self, Iterable<?> args) {
            self.getArgs().empty();
            self.args(args);
            return self;
        }
    }
}
