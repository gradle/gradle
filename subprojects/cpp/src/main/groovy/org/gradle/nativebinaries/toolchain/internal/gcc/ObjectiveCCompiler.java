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
import org.gradle.nativebinaries.language.objectivec.internal.ObjectiveCCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;

public class ObjectiveCCompiler implements Compiler<ObjectiveCCompileSpec> {

    private final CommandLineTool<ObjectiveCCompileSpec> commandLineTool;

    public ObjectiveCCompiler(CommandLineTool<ObjectiveCCompileSpec> commandLineTool, Action<List<String>> argsAction, boolean useCommandFile) {
        ArgsTransformer<ObjectiveCCompileSpec> argsTransformer = new ObjectiveCCompileArgsTransformer();
        argsTransformer = new UserArgsTransformer<ObjectiveCCompileSpec>(argsTransformer, argsAction);
        if (useCommandFile) {
            argsTransformer = new GccOptionsFileArgTransformer<ObjectiveCCompileSpec>(argsTransformer);
        }
        this.commandLineTool = commandLineTool.withArguments(argsTransformer);
    }

    public WorkResult execute(ObjectiveCCompileSpec spec) {
        return commandLineTool.inWorkDirectory(spec.getObjectFileDir()).execute(spec);
    }

    private static class ObjectiveCCompileArgsTransformer extends GccCompilerArgsTransformer<ObjectiveCCompileSpec> {
        protected String getLanguage() {
            return "objective-c";
        }
    }


}
