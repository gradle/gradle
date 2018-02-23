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

import com.google.common.collect.Lists;
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.RelativePathSpec;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The basic implementation for converting {@link PatternSet}s to {@link Spec}s.
 * This implementation only caches the default exclude patterns, as these are always
 * used, no matter which other includes and excludes a {@link PatternSet} has. For an
 * implementation that caches all other patterns as well, see {@link CachingPatternSpecFactory}.
 */
public class PatternSpecFactory {
    public static final PatternSpecFactory INSTANCE = new PatternSpecFactory();
    private final List<String> previousDefaultExcludes = Lists.newArrayList();
    private final Map<Boolean, Spec<FileTreeElement>> defaultExcludeSpecs = new HashMap<Boolean, Spec<FileTreeElement>>(2);

    public Spec<FileTreeElement> createSpec(PatternSet patternSet) {
        return Specs.intersect(createIncludeSpec(patternSet), Specs.negate(createExcludeSpec(patternSet)));
    }

    public Spec<FileTreeElement> createIncludeSpec(PatternSet patternSet) {
        List<Spec<FileTreeElement>> allIncludeSpecs = new ArrayList<Spec<FileTreeElement>>(1 + patternSet.getIncludeSpecs().size());

        if (!patternSet.getIncludes().isEmpty()) {
            allIncludeSpecs.add(createSpec(patternSet.getIncludes(), true, patternSet.isCaseSensitive()));
        }

        allIncludeSpecs.addAll(patternSet.getIncludeSpecs());

        return Specs.union(allIncludeSpecs);
    }

    public Spec<FileTreeElement> createExcludeSpec(PatternSet patternSet) {
        List<Spec<FileTreeElement>> allExcludeSpecs = new ArrayList<Spec<FileTreeElement>>(2 + patternSet.getExcludeSpecs().size());

        if (!patternSet.getExcludes().isEmpty()) {
            allExcludeSpecs.add(createSpec(patternSet.getExcludes(), false, patternSet.isCaseSensitive()));
        }

        allExcludeSpecs.add(getDefaultExcludeSpec(patternSet.isCaseSensitive()));

        allExcludeSpecs.addAll(patternSet.getExcludeSpecs());

        if (allExcludeSpecs.isEmpty()) {
            return Specs.satisfyNone();
        } else {
            return Specs.union(allExcludeSpecs);
        }
    }

    private synchronized Spec<FileTreeElement> getDefaultExcludeSpec(boolean caseSensitive) {
        Spec<FileTreeElement> specs = defaultExcludeSpecs.get(caseSensitive);
        List<String> defaultExcludes = Arrays.asList(DirectoryScanner.getDefaultExcludes());

        if (specs == null) {
            specs = updateDefaultExcludeCache(defaultExcludes, caseSensitive);
        } else if (!previousDefaultExcludes.equals(defaultExcludes)) {
            specs = updateDefaultExcludeCache(defaultExcludes, caseSensitive);
        }

        return specs;
    }

    private Spec<FileTreeElement> updateDefaultExcludeCache(List<String> defaultExcludes, boolean caseSensitive) {
        previousDefaultExcludes.clear();
        previousDefaultExcludes.addAll(defaultExcludes);
        defaultExcludeSpecs.put(caseSensitive, createSpec(defaultExcludes, false, true));
        defaultExcludeSpecs.put(caseSensitive, createSpec(defaultExcludes, false, false));
        return defaultExcludeSpecs.get(caseSensitive);
    }

    protected Spec<FileTreeElement> createSpec(Collection<String> patterns, boolean include, boolean caseSensitive) {
        if (patterns.isEmpty()) {
            return include ? Specs.<FileTreeElement>satisfyAll() : Specs.<FileTreeElement>satisfyNone();
        }

        List<Spec<RelativePath>> matchers = new ArrayList<Spec<RelativePath>>(patterns.size());
        for (String pattern : patterns) {
            Spec<RelativePath> patternMatcher = PatternMatcherFactory.getPatternMatcher(include, caseSensitive, pattern);
            matchers.add(patternMatcher);
        }

        return new RelativePathSpec(Specs.union(matchers));
    }
}
