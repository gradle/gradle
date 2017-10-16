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
package org.gradle.language.c.tasks;

import com.google.common.base.Joiner;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.language.c.internal.DefaultCCompileSpec;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;

/**
 * Compiles C source files into object files.
 */
@Incubating
@CacheableTask
public class CCompile extends AbstractNativeSourceCompileTask {
    @Override
    protected NativeCompileSpec createCompileSpec() {
        return new DefaultCCompileSpec();
    }

    /**
     * The C compiler used, including the type and the version.
     *
     * @since 4.4
     */
    @Input
    protected String getCompiler() {
        NativeToolChainInternal toolChain = (NativeToolChainInternal) getToolChain();
        NativePlatformInternal targetPlatform = (NativePlatformInternal) getTargetPlatform();
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
        String version = toolProvider.getVersion(ToolType.C_COMPILER);
        return Joiner.on(" ").join(((ExtendableToolChain) toolChain).getTypeName(), ToolType.C_COMPILER.getToolName(), version);
    }
}
