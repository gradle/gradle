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
import java.util.List;

public class PatternSpecFactory {

    public Spec<FileTreeElement> createSpec(PatternSet patternSet) {
        return Specs.and(createIncludeSpec(patternSet), Specs.not(createExcludeSpec(patternSet)));
    }

    public Spec<FileTreeElement> createIncludeSpec(PatternSet patternSet) {
        List<Spec<FileTreeElement>> allIncludeSpecs = new ArrayList<Spec<FileTreeElement>>(1 + patternSet.getIncludeSpecs().size());

        if (!patternSet.getIncludes().isEmpty()) {
            allIncludeSpecs.add(createSpec(patternSet.getIncludes(), true, patternSet.isCaseSensitive()));
        }

        allIncludeSpecs.addAll(patternSet.getIncludeSpecs());

        return Specs.or(true, allIncludeSpecs);
    }

    public Spec<FileTreeElement> createExcludeSpec(PatternSet patternSet) {
        List<Spec<FileTreeElement>> allExcludeSpecs = new ArrayList<Spec<FileTreeElement>>(2 + patternSet.getExcludeSpecs().size());

        if (!patternSet.getExcludes().isEmpty()) {
            allExcludeSpecs.add(createSpec(patternSet.getExcludes(), false, patternSet.isCaseSensitive()));
        }

        List<String> defaultExcludes = Arrays.asList(DirectoryScanner.getDefaultExcludes());
        if (!defaultExcludes.isEmpty()) {
            allExcludeSpecs.add(createSpec(defaultExcludes, false, patternSet.isCaseSensitive()));
        }

        allExcludeSpecs.addAll(patternSet.getExcludeSpecs());

        return Specs.or(false, allExcludeSpecs);
    }

    protected Spec<FileTreeElement> createSpec(Collection<String> patterns, boolean include, boolean caseSensitive) {
        List<Spec<FileTreeElement>> matchers = new ArrayList<Spec<FileTreeElement>>(patterns.size());

        for (String pattern : patterns) {
            Spec<RelativePath> patternMatcher = PatternMatcherFactory.getPatternMatcher(include, caseSensitive, pattern);
            matchers.add(new RelativePathSpec(patternMatcher));
        }

        return Specs.or(include, matchers);
    }
}
