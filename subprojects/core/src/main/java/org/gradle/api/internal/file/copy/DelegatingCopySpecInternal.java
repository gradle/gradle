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
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFilePermissions;
import org.gradle.api.file.CopyProcessingSpec;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ExpandDetails;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.ReadOnlyFileTreeElement;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.util.internal.ClosureBackedAction;

import javax.annotation.Nullable;
import java.io.FilterReader;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class DelegatingCopySpecInternal implements CopySpecInternal {

    abstract protected CopySpecInternal getDelegateCopySpec();

    @Override
    public boolean isCaseSensitive() {
        return getDelegateCopySpec().isCaseSensitive();
    }

    @Override
    public void setCaseSensitive(boolean caseSensitive) {
        getDelegateCopySpec().setCaseSensitive(caseSensitive);
    }

    @Override
    public boolean getIncludeEmptyDirs() {
        return getDelegateCopySpec().getIncludeEmptyDirs();
    }

    @Override
    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        getDelegateCopySpec().setIncludeEmptyDirs(includeEmptyDirs);
    }

    @Override
    public DuplicatesStrategy getDuplicatesStrategy() {
        return getDelegateCopySpec().getDuplicatesStrategy();
    }

    @Override
    public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
        getDelegateCopySpec().setDuplicatesStrategy(strategy);
    }

    @Override
    public CopySpec filesMatching(String pattern, Action<? super FileCopyDetails> action) {
        return getDelegateCopySpec().filesMatching(pattern, action);
    }

    @Override
    public CopySpec filesMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action) {
        return getDelegateCopySpec().filesMatching(patterns, action);
    }

    @Override
    public CopySpec filesNotMatching(String pattern, Action<? super FileCopyDetails> action) {
        return getDelegateCopySpec().filesNotMatching(pattern, action);
    }

    @Override
    public CopySpec filesNotMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action) {
        return getDelegateCopySpec().filesNotMatching(patterns, action);
    }

    @Override
    public CopySpec with(CopySpec... sourceSpecs) {
        return getDelegateCopySpec().with(sourceSpecs);
    }

    @Override
    public CopySpec from(Object... sourcePaths) {
        return getDelegateCopySpec().from(sourcePaths);
    }

    @Override
    public CopySpec from(Object sourcePath, final Closure c) {
        return getDelegateCopySpec().from(sourcePath, new ClosureBackedAction<>(c));
    }

    @Override
    public CopySpec from(Object sourcePath, Action<? super CopySpec> configureAction) {
        return getDelegateCopySpec().from(sourcePath, configureAction);
    }

    @Override
    public CopySpec setIncludes(Iterable<String> includes) {
        return getDelegateCopySpec().setIncludes(includes);
    }

    @Override
    public CopySpec setExcludes(Iterable<String> excludes) {
        return getDelegateCopySpec().setExcludes(excludes);
    }

    @Override
    public CopySpec include(String... includes) {
        return getDelegateCopySpec().include(includes);
    }

    @Override
    public CopySpec include(Iterable<String> includes) {
        return getDelegateCopySpec().include(includes);
    }

    @Override
    public CopySpec include(Spec<ReadOnlyFileTreeElement> includeSpec) {
        return getDelegateCopySpec().include(includeSpec);
    }

    @Override
    public CopySpec include(Closure includeSpec) {
        return getDelegateCopySpec().include(includeSpec);
    }

    @Override
    public CopySpec exclude(String... excludes) {
        return getDelegateCopySpec().exclude(excludes);
    }

    @Override
    public CopySpec exclude(Iterable<String> excludes) {
        return getDelegateCopySpec().exclude(excludes);
    }

    @Override
    public CopySpec exclude(Spec<ReadOnlyFileTreeElement> excludeSpec) {
        return getDelegateCopySpec().exclude(excludeSpec);
    }

    @Override
    public CopySpec exclude(Closure excludeSpec) {
        return getDelegateCopySpec().exclude(excludeSpec);
    }

    @Override
    public CopySpec into(Object destPath) {
        return getDelegateCopySpec().into(destPath);
    }

    @Override
    public CopySpec into(Object destPath, Closure configureClosure) {
        return getDelegateCopySpec().into(destPath, configureClosure);
    }

    @Override
    public CopySpec into(Object destPath, Action<? super CopySpec> copySpec) {
        return getDelegateCopySpec().into(destPath, copySpec);
    }

    @Override
    public CopySpec rename(Closure closure) {
        return getDelegateCopySpec().rename(closure);
    }

    @Override
    public CopySpec rename(Transformer<String, String> renamer) {
        return getDelegateCopySpec().rename(renamer);
    }

    @Override
    public CopySpec rename(String sourceRegEx, String replaceWith) {
        return getDelegateCopySpec().rename(sourceRegEx, replaceWith);
    }

    @Override
    public CopyProcessingSpec rename(Pattern sourceRegEx, String replaceWith) {
        return getDelegateCopySpec().rename(sourceRegEx, replaceWith);
    }

    @Override
    public CopySpec filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        return getDelegateCopySpec().filter(properties, filterType);
    }

    @Override
    public CopySpec filter(Class<? extends FilterReader> filterType) {
        return getDelegateCopySpec().filter(filterType);
    }

    @Override
    public CopySpec filter(Closure closure) {
        return getDelegateCopySpec().filter(closure);
    }

    @Override
    public CopySpec filter(Transformer<String, String> transformer) {
        return getDelegateCopySpec().filter(transformer);
    }

    @Override
    public CopySpec expand(Map<String, ?> properties) {
        return getDelegateCopySpec().expand(properties);
    }

    @Override
    public CopySpec expand(Map<String, ?> properties, Action<? super ExpandDetails> action) {
        return getDelegateCopySpec().expand(properties, action);
    }

    @Override
    public CopySpec eachFile(Action<? super FileCopyDetails> action) {
        return getDelegateCopySpec().eachFile(action);
    }

    @Override
    public CopySpec eachFile(Closure closure) {
        return getDelegateCopySpec().eachFile(closure);
    }

    @Override
    public Integer getFileMode() {
        return getDelegateCopySpec().getFileMode();
    }

    @Override
    public CopyProcessingSpec setFileMode(@Nullable Integer mode) {
        return getDelegateCopySpec().setFileMode(mode);
    }

    @Override
    public Property<ConfigurableFilePermissions> getFilePermissions() {
        return getDelegateCopySpec().getFilePermissions();
    }

    @Override
    public CopyProcessingSpec filePermissions(Action<? super ConfigurableFilePermissions> configureAction) {
        return getDelegateCopySpec().filePermissions(configureAction);
    }

    @Override
    public Integer getDirMode() {
        return getDelegateCopySpec().getDirMode();
    }

    @Override
    public CopyProcessingSpec setDirMode(@Nullable Integer mode) {
        return getDelegateCopySpec().setDirMode(mode);
    }

    @Override
    public Property<ConfigurableFilePermissions> getDirPermissions() {
        return getDelegateCopySpec().getDirPermissions();
    }

    @Override
    public CopyProcessingSpec dirPermissions(Action<? super ConfigurableFilePermissions> configureAction) {
        return getDelegateCopySpec().dirPermissions(configureAction);
    }

    @Override
    public Set<String> getIncludes() {
        return getDelegateCopySpec().getIncludes();
    }

    @Override
    public Set<String> getExcludes() {
        return getDelegateCopySpec().getExcludes();
    }

    @Override
    public Iterable<CopySpecInternal> getChildren() {
        return getDelegateCopySpec().getChildren();
    }

    @Override
    public CopySpecInternal addChild() {
        return getDelegateCopySpec().addChild();
    }

    @Override
    public CopySpecInternal addChildBeforeSpec(CopySpecInternal spec) {
        return getDelegateCopySpec().addChildBeforeSpec(spec);
    }

    @Override
    public CopySpecInternal addFirst() {
        return getDelegateCopySpec().addFirst();
    }

    @Override
    public void walk(Action<? super CopySpecResolver> action) {
        getDelegateCopySpec().walk(action);
    }

    @Override
    public CopySpecResolver buildRootResolver() {
        return getDelegateCopySpec().buildRootResolver();
    }

    @Override
    public CopySpecResolver buildResolverRelativeToParent(CopySpecResolver parent) {
        return getDelegateCopySpec().buildResolverRelativeToParent(parent);
    }

    @Override
    public String getFilteringCharset() {
        return getDelegateCopySpec().getFilteringCharset();
    }

    @Override
    public void setFilteringCharset(String charset) {
        getDelegateCopySpec().setFilteringCharset(charset);
    }

    @Override
    public void addChildSpecListener(CopySpecListener listener) {
        getDelegateCopySpec().addChildSpecListener(listener);
    }

    @Override
    public void visit(CopySpecAddress parentPath, CopySpecVisitor visitor) {
        getDelegateCopySpec().visit(parentPath, visitor);
    }

    @Override
    public boolean hasCustomActions() {
        return getDelegateCopySpec().hasCustomActions();
    }

    @Override
    public void appendCachingSafeCopyAction(Action<? super FileCopyDetails> action) {
        getDelegateCopySpec().appendCachingSafeCopyAction(action);
    }

    @Override
    public PatternFilterable getPreserve() {
        return getDelegateCopySpec().getPreserve();
    }

    @Override
    public CopySpecInternal preserve(Action<? super PatternFilterable> action) {
        return getDelegateCopySpec().preserve(action);
    }
}
