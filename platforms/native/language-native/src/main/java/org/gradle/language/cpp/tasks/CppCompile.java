/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.cpp.tasks;

import org.gradle.api.tasks.CacheableTask;
import org.gradle.language.cpp.tasks.internal.DefaultCppCompileSpec;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

/**
 * Compiles C++ source files into object files.
 */
@CacheableTask
public abstract class CppCompile extends AbstractNativeSourceCompileTask {
    @Override
    protected NativeCompileSpec createCompileSpec() {
        return new DefaultCppCompileSpec();
    }

}
