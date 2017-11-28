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

package org.gradle.language.nativeplatform.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.language.base.compile.CompilerVersion;

/**
 * Base class for cacheable source compile tasks.
 *
 * @since 4.4
 */
@Incubating
public abstract class AbstractCacheableNativeSourceCompileTask extends AbstractNativeSourceCompileTask {

    public AbstractCacheableNativeSourceCompileTask() {
        getOutputs().doNotCacheIf("No header dependency analysis provided", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return !getHeaderDependenciesFile().isPresent();
            }
        });
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
        getOutputs().doNotCacheIf("Debug is enabled", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return isDebuggable() && !Boolean.getBoolean("org.gradle.caching.native");
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getSource() {
        return super.getSource();
    }

}
