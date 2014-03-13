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

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.file.*;
import org.gradle.api.internal.ChainingTransformer;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.NotSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.io.FilterReader;
import java.util.*;
import java.util.regex.Pattern;

@NonExtensible
public class DefaultCopySpec implements CopySpecInternal {
    private final FileResolver resolver;
    private final Set<Object> sourcePaths;
    private Object destDir;
    private final PatternSet patternSet;
    private final List<CopySpecInternal> childSpecs;
    private final Instantiator instantiator;
    private final DefaultCopySpec parentSpec;
    private final List<Action<? super FileCopyDetails>> actions = new ArrayList<Action<? super FileCopyDetails>>();
    private Integer dirMode;
    private Integer fileMode;
    private Boolean caseSensitive;
    private Boolean includeEmptyDirs;
    private PathNotationParser<String> pathNotationParser;
    private DuplicatesStrategy duplicatesStrategy;

    public DefaultCopySpec(FileResolver resolver, Instantiator instantiator, DefaultCopySpec parentSpec) {
        this.parentSpec = parentSpec;
        this.resolver = resolver;
        this.instantiator = instantiator;
        this.pathNotationParser = new PathNotationParser<String>();
        sourcePaths = new LinkedHashSet<Object>();
        childSpecs = new ArrayList<CopySpecInternal>();
        patternSet = new PatternSet();
        duplicatesStrategy = null; //inherit from parent
    }

    public DefaultCopySpec(FileResolver resolver, Instantiator instantiator) {
        this(resolver, instantiator, null);
    }

    protected FileResolver getResolver() {
        return resolver;
    }

    public CopySpec with(CopySpec... copySpecs) {
        for (CopySpec copySpec : copySpecs) {
            CopySpecInternal copySpecInternal;
            if (copySpec instanceof CopySpecSource) {
                CopySpecSource copySpecSource = (CopySpecSource) copySpec;
                copySpecInternal = copySpecSource.getRootSpec();
            } else {
                copySpecInternal = (CopySpecInternal) copySpec;
            }
            childSpecs.add(new RelativizedCopySpec(this, copySpecInternal));
        }
        return this;
    }

    public CopySpec from(Object... sourcePaths) {
        for (Object sourcePath : sourcePaths) {
            this.sourcePaths.add(sourcePath);
        }
        return this;
    }

    public CopySpec from(Object sourcePath, Closure c) {
        if (c == null) {
            from(sourcePath);
            return this;
        } else {
            CopySpecInternal child = addChild();
            child.from(sourcePath);
            return ConfigureUtil.configure(c, instantiator.newInstance(CopySpecWrapper.class, child));
        }
    }

    public CopySpecInternal addFirst() {
        return addChildAtPosition(0);
    }

    private CopySpecInternal addChildAtPosition(int position) {
        DefaultCopySpec child = instantiator.newInstance(DefaultCopySpec.class, resolver, instantiator, this);
        childSpecs.add(position, child);
        return child;
    }

    public CopySpecInternal addChild() {
        DefaultCopySpec child = new DefaultCopySpec(resolver, instantiator, this);
        childSpecs.add(child);
        return child;
    }

    public CopySpecInternal addChildBeforeSpec(CopySpecInternal childSpec) {
        int position = childSpecs.indexOf(childSpec);
        return position != -1 ? addChildAtPosition(position) : addChild();
    }

    public Set<Object> getSourcePaths() {
        return sourcePaths;
    }

    public FileTree getSource() {
        return resolver.resolveFilesAsTree(sourcePaths).matching(getPatternSet());
    }

    public FileTree getAllSource() {
        final ImmutableList.Builder<FileTree> builder = ImmutableList.builder();
        walk(new Action<CopySpecInternal>() {
            public void execute(CopySpecInternal copySpecInternal) {
                builder.add(copySpecInternal.getSource());
            }
        });

        return resolver.compositeFileTree(builder.build());
    }

    public DefaultCopySpec into(Object destDir) {
        this.destDir = destDir;
        return this;
    }

    public CopySpec into(Object destPath, Closure configureClosure) {
        if (configureClosure == null) {
            into(destPath);
            return this;
        } else {
            CopySpecInternal child = addChild();
            child.into(destPath);
            return ConfigureUtil.configure(configureClosure, instantiator.newInstance(CopySpecWrapper.class, child));
        }
    }

    public RelativePath getDestPath() {
        RelativePath parentPath;
        if (parentSpec == null) {
            parentPath = new RelativePath(false);
        } else {
            parentPath = parentSpec.getDestPath();
        }
        if (destDir == null) {
            return parentPath;
        }

        String path = resolveToPath(destDir);
        if (path.startsWith("/") || path.startsWith(File.separator)) {
            return RelativePath.parse(false, path);
        }

        return RelativePath.parse(false, parentPath, path);
    }

    private String resolveToPath(Object destDir) {
        return pathNotationParser.parseNotation(destDir);
    }

    public PatternSet getPatternSet() {
        PatternSet patterns = new PatternSet();
        patterns.setCaseSensitive(isCaseSensitive());
        patterns.include(getAllIncludes());
        patterns.includeSpecs(getAllIncludeSpecs());
        patterns.exclude(getAllExcludes());
        patterns.excludeSpecs(getAllExcludeSpecs());
        return patterns;
    }

    public boolean isCaseSensitive() {
        if (caseSensitive != null) {
            return caseSensitive;
        }
        if (parentSpec != null) {
            return parentSpec.isCaseSensitive();
        }
        return true;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public boolean getIncludeEmptyDirs() {
        if (includeEmptyDirs != null) {
            return includeEmptyDirs;
        }
        if (parentSpec != null) {
            return parentSpec.getIncludeEmptyDirs();
        }
        return true;
    }

    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        this.includeEmptyDirs = includeEmptyDirs;
    }

    public DuplicatesStrategy getDuplicatesStrategy() {
        if (duplicatesStrategy != null) {
            return duplicatesStrategy;
        }
        if (parentSpec != null) {
            return parentSpec.getDuplicatesStrategy();
        }
        return DuplicatesStrategy.INCLUDE;
    }

    public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
        this.duplicatesStrategy = strategy;
    }

    public CopySpec filesMatching(String pattern, Action<? super FileCopyDetails> action) {
        Spec<RelativePath> matcher = PatternMatcherFactory.getPatternMatcher(true, isCaseSensitive(), pattern);
        return eachFile(
                new MatchingCopyAction(matcher, action));
    }

    public CopySpec filesNotMatching(String pattern, Action<? super FileCopyDetails> action) {
        Spec<RelativePath> matcher = PatternMatcherFactory.getPatternMatcher(true, isCaseSensitive(), pattern);
        return eachFile(
                new MatchingCopyAction(new NotSpec<RelativePath>(matcher), action));
    }

    public CopySpec include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    public CopySpec include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    public CopySpec include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public CopySpec include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    public CopySpec setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    public List<String> getAllIncludes() {
        List<String> result = new ArrayList<String>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllIncludes());
        }
        result.addAll(getIncludes());
        return result;
    }

    public List<Spec<FileTreeElement>> getAllIncludeSpecs() {
        List<Spec<FileTreeElement>> result = new ArrayList<Spec<FileTreeElement>>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllIncludeSpecs());
        }
        result.addAll(patternSet.getIncludeSpecs());
        return result;
    }

    public CopySpec exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public CopySpec exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public CopySpec exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public CopySpec exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    public DefaultCopySpec setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    public CopySpec rename(String sourceRegEx, String replaceWith) {
        actions.add(new RenamingCopyAction(new RegExpNameMapper(sourceRegEx, replaceWith)));
        return this;
    }

    public CopySpec rename(Pattern sourceRegEx, String replaceWith) {
        actions.add(new RenamingCopyAction(new RegExpNameMapper(sourceRegEx, replaceWith)));
        return this;
    }

    public CopySpec filter(final Class<? extends FilterReader> filterType) {
        actions.add(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(filterType);
            }
        });
        return this;
    }

    public CopySpec filter(final Closure closure) {
        actions.add(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(closure);
            }
        });
        return this;
    }

    public CopySpec filter(final Map<String, ?> properties, final Class<? extends FilterReader> filterType) {
        actions.add(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(properties, filterType);
            }
        });
        return this;
    }

    public CopySpec expand(final Map<String, ?> properties) {
        actions.add(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.expand(properties);
            }
        });
        return this;
    }

    public CopySpec rename(Closure closure) {
        ChainingTransformer<String> transformer = new ChainingTransformer<String>(String.class);
        transformer.add(closure);
        actions.add(new RenamingCopyAction(transformer));
        return this;
    }

    public Integer getDirMode() {
        if (dirMode != null) {
            return dirMode;
        }
        if (parentSpec != null) {
            return parentSpec.getDirMode();
        }
        return null;
    }

    public Integer getFileMode() {
        if (fileMode != null) {
            return fileMode;
        }
        if (parentSpec != null) {
            return parentSpec.getFileMode();
        }
        return null;
    }

    public CopyProcessingSpec setDirMode(Integer mode) {
        dirMode = mode;
        return this;
    }

    public CopyProcessingSpec setFileMode(Integer mode) {
        fileMode = mode;
        return this;
    }

    public CopySpec eachFile(Action<? super FileCopyDetails> action) {
        actions.add(action);
        return this;
    }

    public CopySpec eachFile(Closure closure) {
        actions.add(new ClosureBackedAction<FileCopyDetails>(closure));
        return this;
    }

    public List<String> getAllExcludes() {
        List<String> result = new ArrayList<String>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllExcludes());
        }
        result.addAll(getExcludes());
        return result;
    }

    public List<Spec<FileTreeElement>> getAllExcludeSpecs() {
        List<Spec<FileTreeElement>> result = new ArrayList<Spec<FileTreeElement>>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllExcludeSpecs());
        }
        result.addAll(patternSet.getExcludeSpecs());
        return result;
    }

    public List<Action<? super FileCopyDetails>> getAllCopyActions() {
        if (parentSpec == null) {
            return actions;
        }
        List<Action<? super FileCopyDetails>> allActions = new ArrayList<Action<? super FileCopyDetails>>();
        allActions.addAll(parentSpec.getAllCopyActions());
        allActions.addAll(actions);
        return allActions;
    }

    public Iterable<CopySpecInternal> getChildren() {
        return childSpecs;
    }

    public void walk(Action<? super CopySpecInternal> action) {
        action.execute(this);
        for (CopySpecInternal child : getChildren()) {
            child.walk(action);
        }
    }

    public boolean hasSource() {
        if (!sourcePaths.isEmpty()) {
            return true;
        }
        for (CopySpecInternal spec : childSpecs) {
            if (spec.hasSource()) {
                return true;
            }
        }
        return false;
    }

}
