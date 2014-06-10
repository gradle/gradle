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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotsMaker;
import org.gradle.api.tasks.WorkResult;

class IncrementalCompilationFinalizer implements Compiler<JavaCompileSpec> {

    private final Compiler<JavaCompileSpec> delegate;
    private final JarSnapshotsMaker jarSnapshotsMaker;
    private final ClassDependencyInfoUpdater dependencyInfoUpdater;

    public IncrementalCompilationFinalizer(Compiler<JavaCompileSpec> delegate, JarSnapshotsMaker jarSnapshotsMaker,
                                           ClassDependencyInfoUpdater dependencyInfoUpdater) {
        this.delegate = delegate;
        this.jarSnapshotsMaker = jarSnapshotsMaker;
        this.dependencyInfoUpdater = dependencyInfoUpdater;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        WorkResult out = delegate.execute(spec);

        dependencyInfoUpdater.updateInfo(spec, out);
        jarSnapshotsMaker.storeJarSnapshots(spec.getClasspath());

        return out;
    }
}