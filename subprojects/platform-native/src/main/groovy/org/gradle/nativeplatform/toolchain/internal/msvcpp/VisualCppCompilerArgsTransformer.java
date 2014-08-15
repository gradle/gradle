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

import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArg;
import static org.gradle.nativeplatform.toolchain.internal.msvcpp.EscapeUserArgs.escapeUserArgs;

abstract class VisualCppCompilerArgsTransformer<T extends NativeCompileSpec> implements ArgsTransformer<T> {
    public List<String> transform(T spec) {
        List<String> args = new ArrayList<String>();
        args.add(getLanguageOption());
        args.add("/nologo");

        for (String macroArg : new MacroArgsConverter().transform(spec.getMacros())) {
            args.add(escapeUserArg("/D" + macroArg));
        }
        args.addAll(escapeUserArgs(spec.getAllArgs()));

        args.add("/c");
        for (File file : spec.getIncludeRoots()) {
            args.add("/I" + file.getAbsolutePath());
        }

        return args;
    }

    protected abstract String getLanguageOption();
}
