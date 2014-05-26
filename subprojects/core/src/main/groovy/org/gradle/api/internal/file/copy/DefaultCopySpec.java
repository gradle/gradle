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
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.io.FilterReader;
import java.util.*;
import java.util.regex.Pattern;

@NonExtensible
public class DefaultCopySpec implements CopySpecInternal {
    private static final NotationParser<Object, String> PATH_NOTATION_PARSER = PathNotationParser.create();
    protected final FileResolver fileResolver;
    private final Set<Object> sourcePaths;
    private Object destDir;
    private final PatternSet patternSet;
    protected final List<CopySpecInternal> childSpecs;
    protected final Instantiator instantiator;
    private final List<Action<? super FileCopyDetails>> copyActions = new ArrayList<Action<? super FileCopyDetails>>();
    private Integer dirMode;
    private Integer fileMode;
    private Boolean caseSensitive;
    private Boolean includeEmptyDirs;
    private DuplicatesStrategy duplicatesStrategy;

    public DefaultCopySpec(FileResolver resolver, Instantiator instantiator) {
        this.fileResolver = resolver;
        this.instantiator = instantiator;
        sourcePaths = new LinkedHashSet<Object>();
        childSpecs = new ArrayList<CopySpecInternal>();
        patternSet = new PatternSet();
        duplicatesStrategy = null;
    }

    protected List<Action<? super FileCopyDetails>> getCopyActions() {
        return copyActions;
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
            childSpecs.add(copySpecInternal);
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

    protected CopySpecInternal addChildAtPosition(int position) {
        DefaultCopySpec child = instantiator.newInstance(SingleParentCopySpec.class, fileResolver, instantiator, buildRootResolver());
        childSpecs.add(position, child);
        return child;
    }

    public CopySpecInternal addChild() {
        DefaultCopySpec child = new SingleParentCopySpec(fileResolver, instantiator, buildRootResolver());
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

    public boolean isCaseSensitive() {
        return buildRootResolver().isCaseSensitive();
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public boolean getIncludeEmptyDirs() {
        return buildRootResolver().getIncludeEmptyDirs();
    }

    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        this.includeEmptyDirs = includeEmptyDirs;
    }

    public DuplicatesStrategy getDuplicatesStrategy() {
        return buildRootResolver().getDuplicatesStrategy();
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
        copyActions.add(new RenamingCopyAction(new RegExpNameMapper(sourceRegEx, replaceWith)));
        return this;
    }

    public CopySpec rename(Pattern sourceRegEx, String replaceWith) {
        copyActions.add(new RenamingCopyAction(new RegExpNameMapper(sourceRegEx, replaceWith)));
        return this;
    }

    public CopySpec filter(final Class<? extends FilterReader> filterType) {
        copyActions.add(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(filterType);
            }
        });
        return this;
    }

    public CopySpec filter(final Closure closure) {
        copyActions.add(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(closure);
            }
        });
        return this;
    }

    public CopySpec filter(final Map<String, ?> properties, final Class<? extends FilterReader> filterType) {
        copyActions.add(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(properties, filterType);
            }
        });
        return this;
    }

    public CopySpec expand(final Map<String, ?> properties) {
        copyActions.add(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.expand(properties);
            }
        });
        return this;
    }

    public CopySpec rename(Closure closure) {
        ChainingTransformer<String> transformer = new ChainingTransformer<String>(String.class);
        transformer.add(closure);
        copyActions.add(new RenamingCopyAction(transformer));
        return this;
    }

    public Integer getDirMode() {
        return buildRootResolver().getDirMode();
    }

    public Integer getFileMode() {
        return buildRootResolver().getFileMode();
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
        copyActions.add(action);
        return this;
    }

    public CopySpec eachFile(Closure closure) {
        copyActions.add(new ClosureBackedAction<FileCopyDetails>(closure));
        return this;
    }

    public Iterable<CopySpecInternal> getChildren() {
        return childSpecs;
    }

    public void walk(Action<? super CopySpecResolver> action) {
        buildRootResolver().walk(action);
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

    public CopySpecResolver buildResolverRelativeToParent(CopySpecResolver parent) {
        return this.new DefaultCopySpecResolver(parent);
    }

    public CopySpecResolver buildRootResolver() {
        return this.new DefaultCopySpecResolver(null);
    }

    public class DefaultCopySpecResolver implements CopySpecResolver {

        private CopySpecResolver parentResolver;

        private DefaultCopySpecResolver(CopySpecResolver parent) {
            this.parentResolver = parent;
        }

        public RelativePath getDestPath() {

            RelativePath parentPath;
            if (parentResolver == null) {
                parentPath = new RelativePath(false);
            } else {
                parentPath = parentResolver.getDestPath();
            }

            if (destDir == null) {
                return parentPath;
            }

            String path = PATH_NOTATION_PARSER.parseNotation(destDir);
            if (path.startsWith("/") || path.startsWith(File.separator)) {
                return RelativePath.parse(false, path);
            }

            return RelativePath.parse(false, parentPath, path);
        }

        public FileTree getSource() {
            return fileResolver.resolveFilesAsTree(sourcePaths).matching(this.getPatternSet());
        }

        public FileTree getAllSource() {
            final ImmutableList.Builder<FileTree> builder = ImmutableList.builder();
            walk(new Action<CopySpecResolver>() {
                public void execute(CopySpecResolver copySpecResolver) {
                    builder.add(copySpecResolver.getSource());
                }
            });

            return fileResolver.compositeFileTree(builder.build());
        }

        public Collection<? extends Action<? super FileCopyDetails>> getAllCopyActions() {
            if (parentResolver == null) {
                return copyActions;
            }
            List<Action<? super FileCopyDetails>> allActions = new ArrayList<Action<? super FileCopyDetails>>();
            allActions.addAll(parentResolver.getAllCopyActions());
            allActions.addAll(copyActions);
            return allActions;
        }

        public List<String> getAllIncludes() {
            List<String> result = new ArrayList<String>();
            if (parentResolver != null) {
                result.addAll(parentResolver.getAllIncludes());
            }
            result.addAll(patternSet.getIncludes());
            return result;
        }

        public List<String> getAllExcludes() {
            List<String> result = new ArrayList<String>();
            if (parentResolver != null) {
                result.addAll(parentResolver.getAllExcludes());
            }
            result.addAll(patternSet.getExcludes());
            return result;
        }


        public List<Spec<FileTreeElement>> getAllExcludeSpecs() {
            List<Spec<FileTreeElement>> result = new ArrayList<Spec<FileTreeElement>>();
            if (parentResolver != null) {
                result.addAll(parentResolver.getAllExcludeSpecs());
            }
            result.addAll(patternSet.getExcludeSpecs());
            return result;
        }

        public DuplicatesStrategy getDuplicatesStrategy() {
            if (duplicatesStrategy != null) {
                return duplicatesStrategy;
            }
            if (parentResolver != null) {
                return parentResolver.getDuplicatesStrategy();
            }
            return DuplicatesStrategy.INCLUDE;
        }


        public boolean isCaseSensitive() {
            if (caseSensitive != null) {
                return caseSensitive;
            }
            if (parentResolver != null) {
                return parentResolver.isCaseSensitive();
            }
            return true;
        }

        public Integer getFileMode() {
            if (fileMode != null) {
                return fileMode;
            }
            if (parentResolver != null) {
                return parentResolver.getFileMode();
            }
            return null;
        }

        public Integer getDirMode() {
            if (dirMode != null) {
                return dirMode;
            }
            if (parentResolver != null) {
                return parentResolver.getDirMode();
            }
            return null;
        }

        public boolean getIncludeEmptyDirs() {
            if (includeEmptyDirs != null) {
                return includeEmptyDirs;
            }
            if (parentResolver != null) {
                return parentResolver.getIncludeEmptyDirs();
            }
            return true;
        }

        public List<Spec<FileTreeElement>> getAllIncludeSpecs() {
            List<Spec<FileTreeElement>> result = new ArrayList<Spec<FileTreeElement>>();
            if (parentResolver != null) {
                result.addAll(parentResolver.getAllIncludeSpecs());
            }
            result.addAll(patternSet.getIncludeSpecs());
            return result;
        }

        public PatternSet getPatternSet() {
            PatternSet patterns = new PatternSet();
            patterns.setCaseSensitive(isCaseSensitive());
            patterns.include(this.getAllIncludes());
            patterns.includeSpecs(getAllIncludeSpecs());
            patterns.exclude(this.getAllExcludes());
            patterns.excludeSpecs(getAllExcludeSpecs());
            return patterns;
        }

        public void walk(Action<? super CopySpecResolver> action) {
            action.execute(this);
            for (CopySpecInternal child : getChildren()) {
                child.buildResolverRelativeToParent(this).walk(action);
            }
        }
    }

}

