/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.util.internal;

import groovy.lang.Closure;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import javax.inject.Inject;

/**
 * A lazy version of {@link PatternFilterable} that allows includes and excludes to be added lazily.
 */
@NonNullApi
public abstract class LazyPatternFilterable {

    public abstract SetProperty<String> getIncludes();
    public abstract SetProperty<String> getExcludes();
    public abstract SetProperty<Spec<FileTreeElement>> getIncludeSpecs();
    public abstract SetProperty<Spec<FileTreeElement>> getExcludeSpecs();

    public void include(String[] includes) {
        getIncludes().addAll(includes);
    }

    public void include(Iterable<String> includes) {
        getIncludes().addAll(includes);
    }

    public void include(Spec<FileTreeElement> includeSpec) {
        getIncludeSpecs().add(includeSpec);
    }

    public void include(Closure closure) {
        getIncludeSpecs().add(Specs.convertClosureToSpec(closure));
    }

    public void exclude(String[] excludes) {
        getExcludes().addAll(excludes);
    }

    public void exclude(Iterable<String> excludes) {
        getExcludes().addAll(excludes);
    }

    public void exclude(Spec<FileTreeElement> excludeSpec) {
        getExcludeSpecs().add(excludeSpec);
    }

    public void exclude(Closure excludeSpec) {
        getExcludeSpecs().add(Specs.convertClosureToSpec(excludeSpec));
    }

    public LazyPatternFilterable applyFrom(PatternSet patternSet) {
        include(patternSet.getIncludesView());
        exclude(patternSet.getExcludesView());
        patternSet.getIncludeSpecsView().forEach(this::include);
        patternSet.getExcludeSpecsView().forEach(this::exclude);
        return this;
    }

    /**
     * Applies the include and exclude patterns to the given {@link PatternFilterable}.
     */
    public PatternFilterable applyTo(PatternFilterable patternFilterable) {
        patternFilterable.include(getIncludes().get());
        patternFilterable.exclude(getExcludes().get());
        for (Spec<FileTreeElement> includeSpec : getIncludeSpecs().get()) {
            patternFilterable.include(includeSpec);
        }
        for (Spec<FileTreeElement> excludeSpec : getExcludeSpecs().get()) {
            patternFilterable.exclude(excludeSpec);
        }
        return patternFilterable;
    }

    /**
     * An implementation of LazyPatternFilterable that can be instantiated where ObjectFactory is not available.
     *
     * Instantiating this class directly is not recommended. Use {@link LazyPatternFilterable} if possible.
     */
    @NonNullApi
    public static class DirectInstantiatedLazyPatternFilterable extends LazyPatternFilterable {

        private final SetProperty<String> includes;
        private final SetProperty<String> excludes;
        private final SetProperty<Spec<FileTreeElement>> includeSpecs;
        private final SetProperty<Spec<FileTreeElement>> excludeSpecs;

        @Inject
        public DirectInstantiatedLazyPatternFilterable(
            SetProperty<String> includes,
            SetProperty<String> excludes,
            SetProperty<Spec<FileTreeElement>> includeSpecs,
            SetProperty<Spec<FileTreeElement>> excludeSpecs
        ) {
            this.includes = includes;
            this.excludes = excludes;
            this.includeSpecs = includeSpecs;
            this.excludeSpecs = excludeSpecs;
        }

        @Override
        public SetProperty<String> getIncludes() {
            return includes;
        }

        @Override
        public SetProperty<String> getExcludes() {
            return excludes;
        }

        @Override
        public SetProperty<Spec<FileTreeElement>> getIncludeSpecs() {
            return includeSpecs;
        }

        @Override
        public SetProperty<Spec<FileTreeElement>> getExcludeSpecs() {
            return excludeSpecs;
        }
    }
}
