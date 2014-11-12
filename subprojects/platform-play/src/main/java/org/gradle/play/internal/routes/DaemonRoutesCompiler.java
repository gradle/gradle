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

package org.gradle.play.internal.routes;

import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class DaemonRoutesCompiler extends AbstractDaemonCompiler<VersionedRoutesCompileSpec> {
    private final Iterable<File> compilerClasspath;

    public DaemonRoutesCompiler(File projectDir, RoutesCompiler playRoutesCompiler, CompilerDaemonFactory compilerDaemonFactory, Iterable<File> compilerClasspath) {
        super(projectDir, playRoutesCompiler, compilerDaemonFactory);
        this.compilerClasspath = compilerClasspath;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected DaemonForkOptions toDaemonOptions(VersionedRoutesCompileSpec spec) {
        List<String> routesPackages = spec.getClassLoaderPackages();
        return new DaemonForkOptions(null, null, Collections.EMPTY_LIST, compilerClasspath, routesPackages);
    }
}
