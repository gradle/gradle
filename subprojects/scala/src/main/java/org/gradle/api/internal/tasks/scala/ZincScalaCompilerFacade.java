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
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheRepository;
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.layout.GlobalCacheDir;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;
import java.io.Serializable;

public class ZincScalaCompilerFacade implements Compiler<ScalaJavaJointCompileSpec>, Serializable {
    private final CacheRepository cacheRepository;

    private final HashedClasspath scalaClasspath;

    @Inject
    public ZincScalaCompilerFacade(CacheFactory cacheFactory, GradleUserHomeDirProvider userHomeDirProvider, HashedClasspath scalaClasspath) {
        // TODO: This should be injectable
        DefaultCacheScopeMapping cacheScopeMapping = new DefaultCacheScopeMapping(new GlobalCacheDir(userHomeDirProvider).getDir(), GradleVersion.current());
        this.cacheRepository = new DefaultCacheRepository(cacheScopeMapping, cacheFactory);
        this.scalaClasspath = scalaClasspath;
    }

    @Override
    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
        return ZincScalaCompilerFactory.getCompiler(cacheRepository, scalaClasspath).execute(spec);
    }
}
