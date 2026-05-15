/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.file.RelativePathSpec;
import org.gradle.api.internal.file.pattern.PatternMatcher;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.excludes.FileSystemDefaultExcludesListener;
import org.gradle.internal.file.excludes.GradleDefaultExcludes;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The basic implementation for converting {@link PatternSet}s to {@link Spec}s.
 * This implementation only caches the default exclude patterns, as these are always
 * used, no matter which other includes and excludes a {@link PatternSet} has. For an
 * implementation that caches all other patterns as well, see {@link CachingPatternSpecFactory}.
 */
@ServiceScope(Scope.Global.class)
public class PatternSpecFactory implements FileSystemDefaultExcludesListener {
    public static final PatternSpecFactory INSTANCE = new PatternSpecFactory();
    private Set<String> currentDefaultExcludes = new HashSet<>(GradleDefaultExcludes.DEFAULT_EXCLUDES);
    private final Map<CaseSensitivity, Spec<FileTreeElement>> defaultExcludeSpecCache = new EnumMap<>(CaseSensitivity.class);

    @Override
    public synchronized void onDefaultExcludesChanged(List<String> excludes) {
        Set<String> newDefaults = new HashSet<>(excludes);
        if (!currentDefaultExcludes.equals(newDefaults)) {
            currentDefaultExcludes = newDefaults;
            defaultExcludeSpecCache.clear();
        }
    }

    public Spec<FileTreeElement> createSpec(PatternSet patternSet) {
        return Specs.intersect(createIncludeSpec(patternSet), Specs.negate(createExcludeSpec(patternSet)));
    }

    public Spec<FileTreeElement> createIncludeSpec(PatternSet patternSet) {
        Set<Spec<FileTreeElement>> includeSpecs = patternSet.getIncludeSpecsView();
        List<Spec<FileTreeElement>> allIncludeSpecs = new ArrayList<>(1 + includeSpecs.size());

        Set<String> includes = patternSet.getIncludesView();
        if (!includes.isEmpty()) {
            allIncludeSpecs.add(createSpec(includes, true, patternSet.isCaseSensitive()));
        }

        allIncludeSpecs.addAll(includeSpecs);

        return Specs.union(allIncludeSpecs);
    }

    public Spec<FileTreeElement> createExcludeSpec(PatternSet patternSet) {
        Set<Spec<FileTreeElement>> excludeSpecs = patternSet.getExcludeSpecsView();
        List<Spec<FileTreeElement>> allExcludeSpecs = new ArrayList<>(2 + excludeSpecs.size());

        Set<String> excludes = patternSet.getExcludesView();
        if (!excludes.isEmpty()) {
            allExcludeSpecs.add(createSpec(excludes, false, patternSet.isCaseSensitive()));
        }

        allExcludeSpecs.add(getDefaultExcludeSpec(CaseSensitivity.forCaseSensitive(patternSet.isCaseSensitive())));
        allExcludeSpecs.addAll(excludeSpecs);

        return Specs.union(allExcludeSpecs);
    }

    private synchronized Spec<FileTreeElement> getDefaultExcludeSpec(CaseSensitivity caseSensitivity) {
        // Lazy population: do not call createSpec from a field initializer or constructor.
        // CachingPatternSpecFactory overrides createSpec and relies on its own field that is
        // not yet initialized at superclass-construction time.
        Spec<FileTreeElement> spec = defaultExcludeSpecCache.get(caseSensitivity);
        if (spec == null) {
            spec = createSpec(currentDefaultExcludes, false, caseSensitivity.isCaseSensitive());
            defaultExcludeSpecCache.put(caseSensitivity, spec);
        }
        return spec;
    }

    protected Spec<FileTreeElement> createSpec(Collection<String> patterns, boolean include, boolean caseSensitive) {
        if (patterns.isEmpty()) {
            return include ? Specs.satisfyAll() : Specs.satisfyNone();
        }

        PatternMatcher matcher = PatternMatcherFactory.getPatternsMatcher(include, caseSensitive, patterns);

        return new RelativePathSpec(matcher);
    }

    private enum CaseSensitivity {
        CASE_SENSITIVE(true),
        CASE_INSENSITIVE(false);

        CaseSensitivity(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }

        public static CaseSensitivity forCaseSensitive(boolean caseSensitive) {
            return caseSensitive
                ? CASE_SENSITIVE
                : CASE_INSENSITIVE;
        }

        private final boolean caseSensitive;

        public boolean isCaseSensitive() {
            return caseSensitive;
        }
    }
}
