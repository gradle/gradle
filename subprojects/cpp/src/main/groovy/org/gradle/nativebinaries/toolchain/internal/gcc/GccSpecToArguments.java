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

package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.nativebinaries.toolchain.internal.CommandLineCompilerArgumentsToOptionFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Uses an option file for arguments passed to GCC if possible.
 * Certain GCC options do not function correctly when included in an option file, so include these directly on the command line as well.
 */
class GccSpecToArguments<T extends BinaryToolSpec> implements CompileSpecToArguments<T> {

    private final CompileSpecToArguments<BinaryToolSpec> commandLineOnlyArguments = new CommandLineOnlyArguments();
    private final CompileSpecToArguments<T> generalArguments;
    private final boolean useOptionFile;

    public GccSpecToArguments(CompileSpecToArguments<T> generalArguments, boolean useOptionFile) {
        this.generalArguments = generalArguments;
        this.useOptionFile = useOptionFile;
    }

    private CommandLineCompilerArgumentsToOptionFile<T> withOptionFile(CompileSpecToArguments<T> sourcesToArguments) {
        return new CommandLineCompilerArgumentsToOptionFile<T>(ArgWriter.unixStyleFactory(), sourcesToArguments);
    }

    public void collectArguments(T spec, ArgCollector collector) {
        if (useOptionFile) {
            commandLineOnlyArguments.collectArguments(spec, collector);
            withOptionFile(generalArguments).collectArguments(spec, collector);
        } else {
            generalArguments.collectArguments(spec, collector);
        }
    }

    // Certain options do not function correctly via an option file
    private static class CommandLineOnlyArguments implements CompileSpecToArguments<BinaryToolSpec> {
        private static final List<String> CLI_ONLY_ARGS = Arrays.asList("-m32", "-m64");

        public void collectArguments(BinaryToolSpec spec, ArgCollector collector) {
            List<String> commandLineOnlyArgs = new ArrayList<String>(spec.getAllArgs());
            commandLineOnlyArgs.retainAll(CLI_ONLY_ARGS);
            collector.args(commandLineOnlyArgs);
        }
    }
}
