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

package org.gradle.play.internal.toolchain;

import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.play.internal.spec.PlayCompileSpec;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class DaemonUnversionedPlayCompiler<T extends PlayCompileSpec> extends AbstractDaemonCompiler<T> {
    private final Iterable<File> compilerClasspath;
    private final List<String> classLoaderPackages;

    public DaemonUnversionedPlayCompiler(File projectDir, org.gradle.language.base.internal.compile.Compiler<T> compiler, CompilerDaemonFactory compilerDaemonFactory, Iterable<File> compilerClasspath, List<String> classLoaderPackages) {
        super(projectDir, compiler, compilerDaemonFactory);
        this.compilerClasspath = compilerClasspath;
        this.classLoaderPackages = classLoaderPackages;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected DaemonForkOptions toDaemonOptions(T spec) {
        return new DaemonForkOptions(null, null, Collections.EMPTY_LIST, compilerClasspath, classLoaderPackages);
    }
}
