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
package org.gradle.api.internal.tasks.compile.incremental.analyzer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashCode;
import org.gradle.internal.Factory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class DefaultInMemoryClassAnalysisCache implements ClassAnalysisCache {
    private final ClassAnalysisCache delegate;

    private final Cache<HashCode, ClassAnalysis> inMemoryCache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(5L, TimeUnit.MINUTES)
            .build();

    public DefaultInMemoryClassAnalysisCache(ClassAnalysisCache delegate) {
        this.delegate = delegate;
    }

    @Override
    public ClassAnalysis get(final HashCode key, final Factory<ClassAnalysis> factory) {
        try {
            return inMemoryCache.get(key, new Callable<ClassAnalysis>() {
                @Override
                public ClassAnalysis call() throws Exception {
                    return delegate.get(key, factory);
                }
            });
        } catch (ExecutionException e) {
            return factory.create();
        }
    }
}
