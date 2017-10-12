/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.language.nativeplatform.internal.incremental.HeaderDependenciesCollector;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilerBuilder;
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask;
import org.gradle.language.swift.internal.DefaultSwiftCompileSpec;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;

/**
 * Compiles Swift source files into object files, executables and libraries.
 *
 * @since 4.1
 */
@Incubating
public class SwiftCompile extends AbstractNativeCompileTask {
    private final Property<String> moduleName;

    public SwiftCompile() {
        moduleName = getProject().getObjects().property(String.class);
    }

    @Override
    protected NativeCompileSpec createCompileSpec() {
        SwiftCompileSpec spec = new DefaultSwiftCompileSpec();
        spec.setModuleName(moduleName.getOrNull());
        return spec;
    }

    @Override
    protected IncrementalCompilerBuilder getIncrementalCompilerBuilder() {
        return new IncrementalCompilerBuilder() {
            @Override
            public <T extends NativeCompileSpec> Compiler<T> createIncrementalCompiler(TaskInternal task, Compiler<T> compiler, NativeToolChain toolchain, HeaderDependenciesCollector headerDependenciesCollector) {
                return compiler;
            }
        };
    }

    @Optional
    @Input
    public String getModuleName() {
        return moduleName.getOrNull();
    }

    public void setModuleName(String moduleName) {
        this.moduleName.set(moduleName);
    }

    /**
     * Sets the moduleName property through a {@link Provider}.
     *
     * @since 4.2
     */
    public void setModuleName(Provider<String> moduleName) {
        this.moduleName.set(moduleName);
    }

    @Override
    public void compile(IncrementalTaskInputs inputs) {
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(getOutputs());
        cleaner.setDestinationDir(getObjectFileDir().getAsFile().get());
        cleaner.execute();

        if (getSource().isEmpty()) {
            setDidWork(cleaner.getDidWork());
            return;
        }

        super.compile(inputs);
    }
}
