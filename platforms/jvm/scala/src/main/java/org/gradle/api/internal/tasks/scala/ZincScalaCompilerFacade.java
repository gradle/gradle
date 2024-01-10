/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.language.base.internal.compile.Compiler;

import javax.inject.Inject;
import java.io.Serializable;

public class ZincScalaCompilerFacade implements Compiler<ScalaJavaJointCompileSpec>, Serializable {
    private final GlobalScopedCacheBuilderFactory globalScopedCacheBuilderFactory;

    private final HashedClasspath scalaClasspath;

    @Inject
    public ZincScalaCompilerFacade(GlobalScopedCacheBuilderFactory globalScopedCacheBuilderFactory, HashedClasspath scalaClasspath) {
        this.globalScopedCacheBuilderFactory = globalScopedCacheBuilderFactory;
        this.scalaClasspath = scalaClasspath;
    }

    @Override
    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
        return ZincScalaCompilerFactory.getCompiler(globalScopedCacheBuilderFactory, scalaClasspath).execute(spec);
    }
}
