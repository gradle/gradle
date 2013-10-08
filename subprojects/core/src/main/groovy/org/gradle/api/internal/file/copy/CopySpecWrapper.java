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
import org.gradle.api.file.*;
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
        return delegate.filesMatching(pattern, action);
    }

    public CopySpec filesNotMatching(String pattern, Action<? super FileCopyDetails> action) {
        return delegate.filesNotMatching(pattern, action);
    }

    public CopySpec with(CopySpec... sourceSpecs) {
        return delegate.with(sourceSpecs);
    }

    public CopySpec from(Object... sourcePaths) {
        return delegate.from(sourcePaths);
    }

    public CopySpec from(Object sourcePath, Closure c) {
        return delegate.from(sourcePath, c);
    }

    public CopySpec setIncludes(Iterable<String> includes) {
        return delegate.setIncludes(includes);
    }

    public CopySpec setExcludes(Iterable<String> excludes) {
        return delegate.setExcludes(excludes);
    }

    public CopySpec include(String... includes) {
        return delegate.include(includes);
    }

    public CopySpec include(Iterable<String> includes) {
        return delegate.include(includes);
    }

    public CopySpec include(Spec<FileTreeElement> includeSpec) {
        return delegate.include(includeSpec);
    }

    public CopySpec include(Closure includeSpec) {
        return delegate.include(includeSpec);
    }

    public CopySpec exclude(String... excludes) {
        return delegate.exclude(excludes);
    }

    public CopySpec exclude(Iterable<String> excludes) {
        return delegate.exclude(excludes);
    }

    public CopySpec exclude(Spec<FileTreeElement> excludeSpec) {
        return delegate.exclude(excludeSpec);
    }

    public CopySpec exclude(Closure excludeSpec) {
        return delegate.exclude(excludeSpec);
    }

    public CopySpec into(Object destPath) {
        return delegate.into(destPath);
    }

    public CopySpec into(Object destPath, Closure configureClosure) {
        return delegate.into(destPath, configureClosure);
    }

    public CopySpec rename(Closure closure) {
        return delegate.rename(closure);
    }

    public CopySpec rename(String sourceRegEx, String replaceWith) {
        return delegate.rename(sourceRegEx, replaceWith);
    }

    public CopyProcessingSpec rename(Pattern sourceRegEx, String replaceWith) {
        return delegate.rename(sourceRegEx, replaceWith);
    }

    public CopySpec filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        return delegate.filter(properties, filterType);
    }

    public CopySpec filter(Class<? extends FilterReader> filterType) {
        return delegate.filter(filterType);
    }

    public CopySpec filter(Closure closure) {
        return delegate.filter(closure);
    }

    public CopySpec expand(Map<String, ?> properties) {
        return delegate.expand(properties);
    }

    public CopySpec eachFile(Action<? super FileCopyDetails> action) {
        return delegate.eachFile(action);
    }

    public CopySpec eachFile(Closure closure) {
        return delegate.eachFile(closure);
    }

    public Integer getFileMode() {
        return delegate.getFileMode();
    }

    public CopyProcessingSpec setFileMode(Integer mode) {
        return delegate.setFileMode(mode);
    }

    public Integer getDirMode() {
        return delegate.getDirMode();
    }

    public CopyProcessingSpec setDirMode(Integer mode) {
        return delegate.setDirMode(mode);
    }

    public Set<String> getIncludes() {
        return delegate.getIncludes();
    }

    public Set<String> getExcludes() {
        return delegate.getExcludes();
    }
}
