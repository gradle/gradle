/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu.tasks;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.gosu.GosuCompileSpec;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.language.gosu.internal.toolchain.GosuToolChainInternal;
import org.gradle.language.gosu.GosuPlatform;

import javax.inject.Inject;

/**
 * A platform-aware Gosu compile task.
 */
@Incubating
@ParallelizableTask
public class PlatformGosuCompile extends AbstractGosuCompile {

    private GosuPlatform platform;
    private Closure<FileCollection> orderClasspath;

    @Inject
    public PlatformGosuCompile() {
        super(new BaseGosuCompileOptions());
    }

    public GosuPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(GosuPlatform platform) {
        this.platform = platform;
    }

    @Inject
    protected GosuToolChainInternal getToolChain() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Compiler<GosuCompileSpec> getCompiler(GosuCompileSpec spec) {
        return CompilerUtil.castCompiler(getToolChain().select(getPlatform()).newCompiler(spec.getClass()));
    }

    @Override
    public Closure<FileCollection> getOrderClasspath() {
        return orderClasspath;
    }

    @Override
    public void setOrderClasspath(Closure<FileCollection> orderClasspath) {
        this.orderClasspath = orderClasspath;
    }
}
