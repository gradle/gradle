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

package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.CommandLineTool;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;

public class ObjectiveCCompiler extends NativeCompiler<ObjectiveCCompileSpec> {

    public ObjectiveCCompiler(CommandLineTool commandLineTool, CommandLineToolInvocation baseInvocation, String objectFileSuffix, boolean useCommandFile) {
        super(commandLineTool, baseInvocation, new ObjectiveCCompileArgsTransformer(), objectFileSuffix, useCommandFile);
    }

    private static class ObjectiveCCompileArgsTransformer extends GccCompilerArgsTransformer<ObjectiveCCompileSpec> {
        protected String getLanguage() {
            return "objective-c";
        }
    }


}
