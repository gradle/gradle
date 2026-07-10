/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.internal.initialization.transform.InstrumentationArtifactMetadata;
import org.gradle.api.internal.initialization.transform.InstrumentationDependencyAnalysis;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class CachedInstrumentationAnalysisSerializer implements InstrumentationAnalysisSerializer {

    private final Cache<File, InstrumentationDependencyAnalysis> dependencyAnalysisCache;
    private final Cache<File, Map<String, Set<String>>> typeHierarchyCache;
    private final InstrumentationAnalysisSerializer delegate;

    public CachedInstrumentationAnalysisSerializer(InstrumentationAnalysisSerializer delegate) {
        this.delegate = delegate;
        dependencyAnalysisCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumSize(20_000)
            .build();
        typeHierarchyCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumSize(20_000)
            .build();
    }

    @Override
    public void writeDependencyAnalysis(File output, InstrumentationDependencyAnalysis dependencyAnalysis) {
        delegate.writeDependencyAnalysis(output, dependencyAnalysis);
        dependencyAnalysisCache.put(output, dependencyAnalysis);
    }

    @Override
    public void writeTypeHierarchyAnalysis(File output, Map<String, Set<String>> types) {
        delegate.writeTypeHierarchyAnalysis(output, types);
        typeHierarchyCache.put(output, types);
    }

    @Override
    public InstrumentationDependencyAnalysis readDependencyAnalysis(File output) {
        try {
            return dependencyAnalysisCache.get(output, () -> delegate.readDependencyAnalysis(output));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InstrumentationArtifactMetadata readMetadataOnly(File input) {
        try {
            return dependencyAnalysisCache.get(input, () -> delegate.readDependencyAnalysis(input)).getMetadata();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Set<String>> readTypeHierarchyAnalysis(File input) {
        try {
            return typeHierarchyCache.get(input, () -> delegate.readTypeHierarchyAnalysis(input));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
