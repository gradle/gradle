/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.file.copy;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.file.CopyProcessingSpec;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.specs.Spec;

import java.io.FilterReader;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Wraps another CopySpec impl, only exposing the CopySpec API.
 *
 * Prevents users from accessing "internal" methods on implementations.
 */
@NonExtensible
public class CopySpecWrapper implements CopySpec {

    private final CopySpec delegate;

    public CopySpecWrapper(CopySpec delegate) {
        this.delegate = delegate;
    }

    public boolean isCaseSensitive() {
        return delegate.isCaseSensitive();
    }

    public void setCaseSensitive(boolean caseSensitive) {
        delegate.setCaseSensitive(caseSensitive);
    }

    public boolean getIncludeEmptyDirs() {
        return delegate.getIncludeEmptyDirs();
    }

    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        delegate.setIncludeEmptyDirs(includeEmptyDirs);
    }

    public DuplicatesStrategy getDuplicatesStrategy() {
        return delegate.getDuplicatesStrategy();
    }

    public void setDuplicatesStrategy(@Nullable DuplicatesStrategy strategy) {
        delegate.setDuplicatesStrategy(strategy);
    }

    public CopySpec filesMatching(String pattern, Action<? super FileCopyDetails> action) {
        delegate.filesMatching(pattern, action);
        return this;
    }

    public CopySpec filesMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action) {
        delegate.filesMatching(patterns, action);
        return this;
    }

    public CopySpec filesNotMatching(String pattern, Action<? super FileCopyDetails> action) {
        delegate.filesNotMatching(pattern, action);
        return this;
    }

    public CopySpec filesNotMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action) {
        delegate.filesNotMatching(patterns, action);
        return this;
    }

    public CopySpec with(CopySpec... sourceSpecs) {
        delegate.with(sourceSpecs);
        return this;
    }

    public CopySpec from(Object... sourcePaths) {
        delegate.from(sourcePaths);
        return this;
    }

    public CopySpec from(Object sourcePath, final Closure c) {
        return delegate.from(sourcePath, new ClosureBackedAction<CopySpec>(c));
    }

    public CopySpec from(Object sourcePath, Action<? super CopySpec> configureAction) {
        return delegate.from(sourcePath, configureAction);
    }

    public CopySpec setIncludes(Iterable<String> includes) {
        delegate.setIncludes(includes);
        return this;
    }

    public CopySpec setExcludes(Iterable<String> excludes) {
        delegate.setExcludes(excludes);
        return this;
    }

    public CopySpec include(String... includes) {
        delegate.include(includes);
        return this;
    }

    public CopySpec include(Iterable<String> includes) {
        delegate.include(includes);
        return this;
    }

    public CopySpec include(Spec<FileTreeElement> includeSpec) {
        delegate.include(includeSpec);
        return this;
    }

    public CopySpec include(Closure includeSpec) {
        delegate.include(includeSpec);
        return this;
    }

    public CopySpec exclude(String... excludes) {
        delegate.exclude(excludes);
        return this;
    }

    public CopySpec exclude(Iterable<String> excludes) {
        delegate.exclude(excludes);
        return this;
    }

    public CopySpec exclude(Spec<FileTreeElement> excludeSpec) {
        delegate.exclude(excludeSpec);
        return this;
    }

    public CopySpec exclude(Closure excludeSpec) {
        delegate.exclude(excludeSpec);
        return this;
    }

    public CopySpec into(Object destPath) {
        delegate.into(destPath);
        return this;
    }

    public CopySpec into(Object destPath, Closure configureClosure) {
        return delegate.into(destPath, configureClosure);
    }

    @Override
    public CopySpec into(Object destPath, Action<? super CopySpec> copySpec) {
        return delegate.into(destPath, copySpec);
    }

    public CopySpec rename(final Closure closure) {
        delegate.rename(new Transformer<String, String>() {
            @Override
            public String transform(String s) {
                Object res = closure.call(s);
                return res == null ? null : res.toString();
            }
        });
        return this;
    }

    public CopySpec rename(Transformer<String, String> renamer) {
        delegate.rename(renamer);
        return this;
    }

    public CopySpec rename(String sourceRegEx, String replaceWith) {
        delegate.rename(sourceRegEx, replaceWith);
        return this;
    }

    public CopyProcessingSpec rename(Pattern sourceRegEx, String replaceWith) {
        delegate.rename(sourceRegEx, replaceWith);
        return this;
    }

    public CopySpec filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        delegate.filter(properties, filterType);
        return this;
    }

    public CopySpec filter(Class<? extends FilterReader> filterType) {
        delegate.filter(filterType);
        return this;
    }

    public CopySpec filter(Closure closure) {
        delegate.filter(closure);
        return this;
    }

    @Override
    public CopySpec filter(Transformer<String, String> transformer) {
        delegate.filter(transformer);
        return this;
    }

    public CopySpec expand(Map<String, ?> properties) {
        delegate.expand(properties);
        return this;
    }

    public CopySpec eachFile(Action<? super FileCopyDetails> action) {
        delegate.eachFile(action);
        return this;
    }

    public CopySpec eachFile(Closure closure) {
        delegate.eachFile(closure);
        return this;
    }

    public Integer getFileMode() {
        return delegate.getFileMode();
    }

    public CopyProcessingSpec setFileMode(Integer mode) {
        delegate.setFileMode(mode);
        return this;
    }

    public Integer getDirMode() {
        return delegate.getDirMode();
    }

    public CopyProcessingSpec setDirMode(Integer mode) {
        delegate.setDirMode(mode);
        return this;
    }

    public Set<String> getIncludes() {
        return delegate.getIncludes();
    }

    public Set<String> getExcludes() {
        return delegate.getExcludes();
    }

    public String getFilteringCharset() {
        return delegate.getFilteringCharset();
    }

    public void setFilteringCharset(String charset) {
        delegate.setFilteringCharset(charset);
    }
}
