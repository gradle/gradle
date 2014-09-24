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
package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

public class IncrementalCompilerBuilder {
    private final TaskArtifactStateCacheAccess cacheAccess;
    private final FileSnapshotter fileSnapshotter;

    public IncrementalCompilerBuilder(TaskArtifactStateCacheAccess cacheAccess, FileSnapshotter fileSnapshotter) {
        this.cacheAccess = cacheAccess;
        this.fileSnapshotter = fileSnapshotter;
    }

    public Compiler<NativeCompileSpec> createIncrementalCompiler(TaskInternal task, Compiler<NativeCompileSpec> compiler, NativeToolChain toolchain) {
        return new IncrementalNativeCompiler(task, cacheAccess, fileSnapshotter, compiler, toolchain);
    }
}
