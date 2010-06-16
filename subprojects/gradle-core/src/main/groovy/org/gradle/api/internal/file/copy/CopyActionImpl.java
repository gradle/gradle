/*
 * Copyright 2010 the original author or authors.  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at       http://www.apache.org/licenses/LICENSE-2.0  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.gradle.api.internal.file.copy;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.specs.Spec;

import java.io.FilterReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Steve Appling
 */
public class CopyActionImpl implements CopyAction {
    private final CopySpecVisitor visitor;
    private final CopySpecImpl root;
    private final CopySpecImpl mainContent;
    private final FileResolver resolver;

    public CopyActionImpl(FileResolver resolver, CopySpecVisitor visitor) {
        this.resolver = resolver;
        root = new CopySpecImpl(resolver);
        mainContent = root.addChild();
        this.visitor = new MappingCopySpecVisitor(new NormalizingCopySpecVisitor(visitor));
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
        return mainContent.eachFile(action);
    }

    public CopySpec eachFile(Closure closure) {
        return mainContent.eachFile(closure);
    }

    public CopySpec exclude(Iterable<String> excludes) {
        return mainContent.exclude(excludes);
    }

    public CopySpec exclude(String... excludes) {
        return mainContent.exclude(excludes);
    }

    public CopySpec exclude(Closure excludeSpec) {
        return mainContent.exclude(excludeSpec);
    }

    public CopySpec exclude(Spec<FileTreeElement> excludeSpec) {
        return mainContent.exclude(excludeSpec);
    }

    public CopySpec expand(Map<String, ?> properties) {
        return mainContent.expand(properties);
    }

    public CopySpec filter(Closure closure) {
        return mainContent.filter(closure);
    }

    public CopySpec filter(Class<? extends FilterReader> filterType) {
        return mainContent.filter(filterType);
    }

    public CopySpec filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        return mainContent.filter(properties, filterType);
    }

    public CopySpec from(Object sourcePath, Closure c) {
        return mainContent.from(sourcePath, c);
    }

    public CopySpec from(Object... sourcePaths) {
        return mainContent.from(sourcePaths);
    }

    public Set<String> getExcludes() {
        return mainContent.getExcludes();
    }

    public Set<String> getIncludes() {
        return mainContent.getIncludes();
    }

    public CopySpec include(Iterable<String> includes) {
        return mainContent.include(includes);
    }

    public CopySpec include(String... includes) {
        return mainContent.include(includes);
    }

    public CopySpec include(Closure includeSpec) {
        return mainContent.include(includeSpec);
    }

    public CopySpec include(Spec<FileTreeElement> includeSpec) {
        return mainContent.include(includeSpec);
    }

    public CopySpec into(Object destDir) {
        return mainContent.into(destDir);
    }

    public CopySpec into(Object destPath, Closure configureClosure) {
        return mainContent.into(destPath, configureClosure);
    }

    public boolean isCaseSensitive() {
        return mainContent.isCaseSensitive();
    }

    public CopySpec rename(Closure closure) {
        return mainContent.rename(closure);
    }

    public CopySpec rename(Pattern sourceRegEx, String replaceWith) {
        return mainContent.rename(sourceRegEx, replaceWith);
    }

    public CopySpec rename(String sourceRegEx, String replaceWith) {
        return mainContent.rename(sourceRegEx, replaceWith);
    }

    public void setCaseSensitive(boolean caseSensitive) {
        mainContent.setCaseSensitive(caseSensitive);
    }

    public int getDirMode() {
        return mainContent.getDirMode();
    }

    public CopyProcessingSpec setDirMode(int mode) {
        return mainContent.setDirMode(mode);
    }

    public CopySpecImpl setExcludes(Iterable<String> excludes) {
        return mainContent.setExcludes(excludes);
    }

    public int getFileMode() {
        return mainContent.getFileMode();
    }

    public CopyProcessingSpec setFileMode(int mode) {
        return mainContent.setFileMode(mode);
    }

    public CopySpec setIncludes(Iterable<String> includes) {
        return mainContent.setIncludes(includes);
    }

    public CopySpec with(CopySpec copySpec) {
        return mainContent.with(copySpec);
    }
}
