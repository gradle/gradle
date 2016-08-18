/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.StaleClassCleaner;

/**
 * Deletes stale classes before invoking the actual compiler
 */
public abstract class CleaningJavaCompilerSupport<T extends JavaCompileSpec> implements org.gradle.language.base.internal.compile.Compiler<T> {
    @Override
    public WorkResult execute(T spec) {
        StaleClassCleaner cleaner = createCleaner(spec);

        cleaner.setDestinationDir(spec.getDestinationDir());
        cleaner.setSource(spec.getSource());
        cleaner.execute();

        Compiler<? super T> compiler = getCompiler();
        return compiler.execute(spec);
    }

    protected abstract Compiler<T> getCompiler();

    protected abstract StaleClassCleaner createCleaner(T spec);
}
