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

package org.gradle.nativecode.toolchain.internal.gpp;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.nativecode.base.internal.BinaryToolSpec;
import org.gradle.nativecode.toolchain.internal.CommandLineCompilerArgumentsToOptionFile;

/**
 * Some GCC options do not function correctly when included in an option file, so only use the option file for include paths and source files.
 */
public class GccCompileSpecToArguments<T extends BinaryToolSpec> implements CompileSpecToArguments<T> {
    private final CompileSpecToArguments<T> optionsToArguments;
    private final CompileSpecToArguments<T> sourcesToArguments;

    public GccCompileSpecToArguments(CompileSpecToArguments<T> optionsToArguments, CompileSpecToArguments<T> sourcesToArguments, boolean useOptionFile) {
        this.optionsToArguments = optionsToArguments;

        // Only use an option file for header paths and source files (some other options don't function correctly in option file
        if (useOptionFile) {
            this.sourcesToArguments = withOptionFile(sourcesToArguments);
        } else {
            this.sourcesToArguments = sourcesToArguments;
        }
    }

    private CommandLineCompilerArgumentsToOptionFile<T> withOptionFile(CompileSpecToArguments<T> sourcesToArguments) {
        return new CommandLineCompilerArgumentsToOptionFile<T>(ArgWriter.unixStyleFactory(), sourcesToArguments);
    }

    public void collectArguments(T spec, ArgCollector collector) {
        optionsToArguments.collectArguments(spec, collector);
        sourcesToArguments.collectArguments(spec, collector);
    }
}
