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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import com.google.common.collect.Lists;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

import java.io.File;
import java.util.List;

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArg;
import static org.gradle.nativeplatform.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArgs;

abstract class VisualCppCompilerArgsTransformer<T extends NativeCompileSpec> implements ArgsTransformer<T> {
    @Override
    public List<String> transform(T spec) {
        List<String> args = Lists.newArrayList();
        addToolSpecificArgs(spec, args);
        addMacroArgs(spec, args);
        addUserArgs(spec, args);
        addIncludeArgs(spec, args);
        return args;
    }

    private void addUserArgs(T spec, List<String> args) {
        args.addAll(escapeUserArgs(spec.getAllArgs()));
    }

    protected void addToolSpecificArgs(T spec, List<String> args) {
        args.add(getLanguageOption());
        args.add("/nologo");
        args.add("/c");
    }

    protected void addIncludeArgs(T spec, List<String> args) {
        for (File file : spec.getIncludeRoots()) {
            args.add("/I" + file.getAbsolutePath());
        }
    }

    protected void addMacroArgs(T spec, List<String> args) {
        for (String macroArg : new MacroArgsConverter().transform(spec.getMacros())) {
            args.add(escapeUserArg("/D" + macroArg));
        }
    }

    /**
     * Returns compiler specific language option
     * @return compiler language option or empty string if the language does not require it
     */
    protected String getLanguageOption() {
        return "";
    }
}
