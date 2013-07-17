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

package org.gradle.nativecode.language.cpp.tasks

import org.gradle.api.Incubating
import org.gradle.api.tasks.WorkResult
import org.gradle.nativecode.base.internal.ToolChainInternal
import org.gradle.nativecode.language.cpp.internal.DefaultCppCompileSpec
import org.gradle.nativecode.language.cpp.internal.NativeCompileSpec

/**
 * Compiles C++ source files into object files.
 */
@Incubating
class CppCompile  extends AbstractNativeCompileTask {
    @Override
    protected NativeCompileSpec createCompileSpec() {
        new DefaultCppCompileSpec()
    }

    @Override
    protected WorkResult execute(ToolChainInternal toolChain, NativeCompileSpec spec) {
        return toolChain.createCppCompiler().execute(spec)
    }
}