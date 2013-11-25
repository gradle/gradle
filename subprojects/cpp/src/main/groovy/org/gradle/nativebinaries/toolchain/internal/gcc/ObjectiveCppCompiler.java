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

package org.gradle.nativebinaries.toolchain.internal.gcc;

import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativebinaries.language.objectivecpp.internal.ObjectiveCppCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

public class ObjectiveCppCompiler implements Compiler<ObjectiveCppCompileSpec> {

    private final CommandLineTool<ObjectiveCppCompileSpec> commandLineTool;

    public ObjectiveCppCompiler(CommandLineTool<ObjectiveCppCompileSpec> commandLineTool, Action<List<String>> argsAction, boolean useCommandFile) {
        ArgsTransformer<ObjectiveCppCompileSpec> argsTransformer = new ObjectiveCppCompileArgsTransformer();
        argsTransformer = new UserArgsTransformer<ObjectiveCppCompileSpec>(argsTransformer, argsAction);
        if (useCommandFile) {
            argsTransformer = new GccOptionsFileArgTransformer<ObjectiveCppCompileSpec>(argsTransformer);
        }
        this.commandLineTool = commandLineTool.withArguments(argsTransformer);
    }

    public WorkResult execute(ObjectiveCppCompileSpec spec) {
        return commandLineTool.inWorkDirectory(spec.getObjectFileDir()).execute(spec);
    }

    private static class ObjectiveCppCompileArgsTransformer extends GccCompilerArgsTransformer<ObjectiveCppCompileSpec> {
        protected String getLanguage() {
            return "objective-c++";
        }
    }

}
