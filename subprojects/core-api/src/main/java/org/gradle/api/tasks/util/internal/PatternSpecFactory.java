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

import org.apache.tools.ant.DirectoryScanner;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.file.RelativePathSpec;
import org.gradle.api.internal.file.pattern.PatternMatcher;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.excludes.FileSystemDefaultExcludesListener;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
    private Set<String> previousDefaultExcludes = new HashSet<String>();
    private final Map<CaseSensitivity, Spec<FileTreeElement>> defaultExcludeSpecCache = new EnumMap<>(CaseSensitivity.class);

    @Override
    public void onDefaultExcludesChanged(List<String> excludes) {
        setDefaultExcludesFromSettings(excludes.toArray(new String[0]));
    }

    public Spec<FileTreeElement> createSpec(PatternSet patternSet) {
        return Specs.intersect(createIncludeSpec(patternSet), Specs.negate(createExcludeSpec(patternSet)));
    }

    public Spec<FileTreeElement> createIncludeSpec(PatternSet patternSet) {
        List<Spec<FileTreeElement>> allIncludeSpecs = new ArrayList<>(1 + patternSet.getIncludeSpecs().size());

        if (!patternSet.getIncludes().isEmpty()) {
            allIncludeSpecs.add(createSpec(patternSet.getIncludes(), true, patternSet.isCaseSensitive()));
        }

        allIncludeSpecs.addAll(patternSet.getIncludeSpecs());

        return Specs.union(allIncludeSpecs);
    }

    public Spec<FileTreeElement> createExcludeSpec(PatternSet patternSet) {
        List<Spec<FileTreeElement>> allExcludeSpecs = new ArrayList<>(2 + patternSet.getExcludeSpecs().size());

        if (!patternSet.getExcludes().isEmpty()) {
            allExcludeSpecs.add(createSpec(patternSet.getExcludes(), false, patternSet.isCaseSensitive()));
        }

        allExcludeSpecs.add(getDefaultExcludeSpec(CaseSensitivity.forCaseSensitive(patternSet.isCaseSensitive())));
        allExcludeSpecs.addAll(patternSet.getExcludeSpecs());

        if (allExcludeSpecs.isEmpty()) {
            return Specs.satisfyNone();
        } else {
            return Specs.union(allExcludeSpecs);
        }
    }

    private synchronized Spec<FileTreeElement> getDefaultExcludeSpec(CaseSensitivity caseSensitivity) {
        Set<String> defaultExcludes = new HashSet<String>(Arrays.asList(DirectoryScanner.getDefaultExcludes()));
        if (defaultExcludeSpecCache.isEmpty()) {
            updateDefaultExcludeSpecCache(defaultExcludes);
        } else if (invalidChangeOfExcludes(defaultExcludes)) {
            failOnChangedDefaultExcludes(previousDefaultExcludes, defaultExcludes);
        }

        return defaultExcludeSpecCache.get(caseSensitivity);
    }

    private boolean invalidChangeOfExcludes(Set<String> defaultExcludes) {
        return !previousDefaultExcludes.equals(defaultExcludes);
    }

    private void failOnChangedDefaultExcludes(Set<String> excludesFromSettings, Set<String> newDefaultExcludes) {
        List<String> sortedExcludesFromSettings = new ArrayList<String>(excludesFromSettings);
        sortedExcludesFromSettings.sort(Comparator.naturalOrder());
        List<String> sortedNewExcludes = new ArrayList<String>(newDefaultExcludes);
        sortedNewExcludes.sort(Comparator.naturalOrder());
        throw new InvalidUserCodeException(String.format("Cannot change default excludes during the build. They were changed from %s to %s. Configure default excludes in the settings script instead.",  sortedExcludesFromSettings, sortedNewExcludes));
    }

    public synchronized void setDefaultExcludesFromSettings(String[] excludesFromSettings) {
        Set<String> excludesFromSettingsSet = new HashSet<String>(Arrays.asList(excludesFromSettings));
        if (!previousDefaultExcludes.equals(excludesFromSettingsSet)) {
            updateDefaultExcludeSpecCache(excludesFromSettingsSet);
        }
    }

    private void updateDefaultExcludeSpecCache(Set<String> defaultExcludes) {
        previousDefaultExcludes = defaultExcludes;
        for (CaseSensitivity caseSensitivity : CaseSensitivity.values()) {
            defaultExcludeSpecCache.put(caseSensitivity, createSpec(defaultExcludes, false, caseSensitivity.isCaseSensitive()));
        }
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
