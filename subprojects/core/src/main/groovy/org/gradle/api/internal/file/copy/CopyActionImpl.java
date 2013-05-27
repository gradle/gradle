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
    private final FileResolver resolver;

    public CopyActionImpl(FileResolver resolver, CopySpecVisitor visitor) {
        this.resolver = resolver;
        root = new CopySpecImpl(resolver);
        mainContent = root.addChild();
        this.visitor = new MappingCopySpecVisitor(
                           new DuplicateHandlingCopySpecVisitor(
                               new NormalizingCopySpecVisitor(visitor)),
                           FileSystems.getDefault());
    }

    public boolean isWarnOnIncludeDuplicates() {
        return true;
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
        visitor.startVisit(this);
        for (ReadableCopySpec spec : root.getAllSpecs()) {
            visitor.visitSpec(spec);
            spec.getSource().visit(visitor);
        }
        visitor.endVisit();
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
