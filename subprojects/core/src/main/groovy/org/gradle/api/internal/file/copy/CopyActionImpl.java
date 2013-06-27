/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.file.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.specs.Spec;
import org.gradle.internal.nativeplatform.filesystem.FileSystems;
import org.gradle.internal.reflect.Instantiator;

import java.io.FilterReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Steve Appling
 */
public class CopyActionImpl implements CopyAction, CopySpecSource {
    private final CopySpecVisitor visitor;
    private final CopySpecImpl root;
    private final CopySpecImpl mainContent;
    private final Instantiator instantiator;
    private final FileResolver resolver;

    public CopyActionImpl(Instantiator instantiator, FileResolver resolver, CopySpecVisitor visitor) {
        this(instantiator, resolver, visitor, false);
    }

    public CopyActionImpl(Instantiator instantiator, FileResolver resolver, CopySpecVisitor visitor, boolean warnOnIncludeDuplicate) {
        this.instantiator = instantiator;
        this.resolver = resolver;
        this.root = instantiator.newInstance(CopySpecImpl.class, resolver, instantiator);
        this.mainContent = root.addChild();
        this.visitor = new DuplicateHandlingCopySpecVisitor(
                new NormalizingCopySpecVisitor(visitor),
                warnOnIncludeDuplicate
        );
    }

    public FileResolver getResolver() {
        return resolver;
    }

    public CopySpecImpl getRootSpec() {
        return root;
    }

    public CopySpecImpl getMainSpec() {
        return mainContent;
    }

    public void execute() {
        CopySpecVisitorDriver driver = new CopySpecVisitorDriver(instantiator, FileSystems.getDefault());
        driver.visit(this, root.getAllSpecs(), visitor);
    }

    public boolean getDidWork() {
        return visitor.getDidWork();
    }

    public FileTree getAllSource() {
        List<FileTree> sources = new ArrayList<FileTree>();
        for (ReadableCopySpec spec : root.getAllSpecs()) {
            FileTree source = spec.getSource();
            sources.add(source);
        }
        return resolver.resolveFilesAsTree(sources);
    }

    public boolean hasSource() {
        return root.hasSource();
    }

    public CopySpec eachFile(Action<? super FileCopyDetails> action) {
        mainContent.eachFile(action);
        return this;
    }

    public CopySpec eachFile(Closure closure) {
        mainContent.eachFile(closure);
        return this;
    }

    public CopySpec exclude(Iterable<String> excludes) {
        mainContent.exclude(excludes);
        return this;
    }

    public CopySpec exclude(String... excludes) {
        mainContent.exclude(excludes);
        return this;
    }

    public CopySpec exclude(Closure excludeSpec) {
        mainContent.exclude(excludeSpec);
        return this;
    }

    public CopySpec exclude(Spec<FileTreeElement> excludeSpec) {
        mainContent.exclude(excludeSpec);
        return this;
    }

    public CopySpec expand(Map<String, ?> properties) {
        mainContent.expand(properties);
        return this;
    }

    public CopySpec filter(Closure closure) {
        mainContent.filter(closure);
        return this;
    }

    public CopySpec filter(Class<? extends FilterReader> filterType) {
        mainContent.filter(filterType);
        return this;
    }

    public CopySpec filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        mainContent.filter(properties, filterType);
        return this;
    }

    public CopySpec from(Object sourcePath, Closure c) {
        return mainContent.from(sourcePath, c);
    }

    public CopySpec from(Object... sourcePaths) {
        mainContent.from(sourcePaths);
        return this;
    }

    public Set<String> getExcludes() {
        return mainContent.getExcludes();
    }

    public Set<String> getIncludes() {
        return mainContent.getIncludes();
    }

    public CopySpec include(Iterable<String> includes) {
        mainContent.include(includes);
        return this;
    }

    public CopySpec include(String... includes) {
        mainContent.include(includes);
        return this;
    }

    public CopySpec include(Closure includeSpec) {
        mainContent.include(includeSpec);
        return this;
    }

    public CopySpec include(Spec<FileTreeElement> includeSpec) {
        mainContent.include(includeSpec);
        return this;
    }

    public CopySpec into(Object destDir) {
        mainContent.into(destDir);
        return this;
    }

    public CopySpec into(Object destPath, Closure configureClosure) {
        return mainContent.into(destPath, configureClosure);
    }

    public boolean isCaseSensitive() {
        return mainContent.isCaseSensitive();
    }

    public boolean getIncludeEmptyDirs() {
        return mainContent.getIncludeEmptyDirs();
    }

    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        mainContent.setIncludeEmptyDirs(includeEmptyDirs);
    }

    public DuplicatesStrategy getDuplicatesStrategy() {
        // see note below for why this is root and not mainContent
        return root.getDuplicatesStrategy();
    }

    public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
        // this is root and not mainContent because eg, Jar.metaInf extends from root,
        // and we want it to inherit the duplicates strategy too
        root.setDuplicatesStrategy(strategy);
    }

    public CopySpec filesMatching(String pattern, Closure closure) {
        return mainContent.filesMatching(pattern, closure);
    }

    public CopySpec filesMatching(String pattern, Action<? super FileCopyDetails> action) {
        return mainContent.filesMatching(pattern, action);
    }

    public CopySpec filesNotMatching(String pattern, Closure closure) {
        return mainContent.filesNotMatching(pattern, closure);
    }

    public CopySpec filesNotMatching(String pattern, Action<? super FileCopyDetails> action) {
        return mainContent.filesNotMatching(pattern, action);
    }

    public CopySpec rename(Closure closure) {
        mainContent.rename(closure);
        return this;
    }

    public CopySpec rename(Pattern sourceRegEx, String replaceWith) {
        mainContent.rename(sourceRegEx, replaceWith);
        return this;
    }

    public CopySpec rename(String sourceRegEx, String replaceWith) {
        mainContent.rename(sourceRegEx, replaceWith);
        return this;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        mainContent.setCaseSensitive(caseSensitive);
    }

    public Integer getDirMode() {
        return mainContent.getDirMode();
    }

    public CopyProcessingSpec setDirMode(Integer mode) {
        mainContent.setDirMode(mode);
        return this;
    }

    public CopySpec setExcludes(Iterable<String> excludes) {
        mainContent.setExcludes(excludes);
        return this;
    }

    public Integer getFileMode() {
        return mainContent.getFileMode();
    }

    public CopyProcessingSpec setFileMode(Integer mode) {
        mainContent.setFileMode(mode);
        return this;
    }

    public CopySpec setIncludes(Iterable<String> includes) {
        mainContent.setIncludes(includes);
        return this;
    }

    public CopySpec with(CopySpec... copySpecs) {
        mainContent.with(copySpecs);
        return this;
    }
}
