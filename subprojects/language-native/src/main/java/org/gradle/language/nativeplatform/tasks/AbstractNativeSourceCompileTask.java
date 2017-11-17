/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.nativeplatform.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.language.base.compile.CompilerVersion;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.VersionAwareCompiler;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultInclude;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PCHUtils;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Compiles native source files into object files.
 */
@Incubating
public abstract class AbstractNativeSourceCompileTask extends AbstractNativeCompileTask {
    private PreCompiledHeader preCompiledHeader;

    public AbstractNativeSourceCompileTask() {
        super();
        getOutputs().doNotCacheIf("Pre-compiled headers are used", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return getPreCompiledHeader() != null;
            }
        });
        getOutputs().doNotCacheIf("Could not determine compiler version", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                CompilerVersion compilerVersion = getCompilerVersion();
                return compilerVersion == null;
            }
        });
    }

    @Override
    protected void configureSpec(NativeCompileSpec spec) {
        super.configureSpec(spec);
        if (preCompiledHeader != null) {
            File pchObjectFile = preCompiledHeader.getObjectFile();
            File pchDir = PCHUtils.generatePCHObjectDirectory(spec.getTempDir(), preCompiledHeader.getPrefixHeaderFile(), pchObjectFile);
            spec.setPrefixHeaderFile(new File(pchDir, preCompiledHeader.getPrefixHeaderFile().getName()));
            spec.setPreCompiledHeaderObjectFile(new File(pchDir, pchObjectFile.getName()));
            spec.setPreCompiledHeader(DefaultInclude.parse(preCompiledHeader.getIncludeString(), true).getValue());
        }
    }

    /**
     * Returns the pre-compiled header to be used during compilation
     */
    @Nested @Optional
    public PreCompiledHeader getPreCompiledHeader() {
        return preCompiledHeader;
    }

    public void setPreCompiledHeader(PreCompiledHeader preCompiledHeader) {
        this.preCompiledHeader = preCompiledHeader;
    }

    /**
     * The compiler used, including the type and the version.
     *
     * @since 4.4
     */
    @Nested
    @Optional
    @Nullable
    protected CompilerVersion getCompilerVersion() {
        NativeToolChainInternal toolChain = (NativeToolChainInternal) getToolChain();
        NativePlatformInternal targetPlatform = (NativePlatformInternal) getTargetPlatform();
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
        Compiler<? extends NativeCompileSpec> compiler = toolProvider.newCompiler(createCompileSpec().getClass());
        if (!(compiler instanceof VersionAwareCompiler)) {
            return null;
        }
        return ((VersionAwareCompiler) compiler).getVersion();
    }
}
