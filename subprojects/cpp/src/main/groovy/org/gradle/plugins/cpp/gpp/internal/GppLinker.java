/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugins.cpp.gpp.internal;

import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.internal.Factory;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompiler;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompilerArgumentsToOptionFile;
import org.gradle.plugins.cpp.internal.LinkerSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

public class GppLinker extends CommandLineCppCompiler<LinkerSpec> {

    public GppLinker(File executable, Factory<ExecAction> execActionFactory, boolean useCommandFile) {
        super(executable, execActionFactory, useCommandFile ? viaCommandFile() : withoutCommandFile());
    }

    private static GppLinkerSpecToArguments withoutCommandFile() {
        return new GppLinkerSpecToArguments();
    }

    private static CommandLineCppCompilerArgumentsToOptionFile<LinkerSpec> viaCommandFile() {
        return new CommandLineCppCompilerArgumentsToOptionFile<LinkerSpec>(
            ArgWriter.unixStyleFactory(), new GppLinkerSpecToArguments()
        );
    }

}
