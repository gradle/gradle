/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.*;
import org.gradle.api.internal.file.copy.CopyActionImpl;
import org.gradle.api.internal.file.copy.FileCopyActionImpl;
import org.gradle.api.internal.file.copy.FileCopySpecVisitor;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Hans Dockter
 */
public class FileSet extends AbstractFileTree implements ConfigurableFileTree {
    private PatternSet patternSet = new PatternSet();
    private Object dir;
    private final FileResolver resolver;

    public FileSet(Object dir, FileResolver resolver) {
        this(Collections.singletonMap("dir", dir), resolver);
    }

    public FileSet(Map<String, ?> args, FileResolver resolver) {
        this.resolver = resolver != null ? resolver : new IdentityFileResolver();
        ConfigureUtil.configureByMap(args, this);
    }

    public PatternSet getPatternSet() {
        return patternSet;
    }

    public void setPatternSet(PatternSet patternSet) {
        this.patternSet = patternSet;
    }

    public FileSet setDir(Object dir) {
        from(dir);
        return this;
    }

    public File getDir() {
        if (dir == null) {
            throw new InvalidUserDataException("A base directory must be specified in the task or via a method argument!");
        }
        return resolver.resolve(dir);
    }

    public FileSet from(Object dir) {
        this.dir = dir;
        return this;
    }

    public String getDisplayName() {
        return String.format("file set '%s'", dir);
    }

    public FileTree matching(PatternFilterable patterns) {
        PatternSet patternSet = this.patternSet.intersect();
        patternSet.copyFrom(patterns);
        FileSet filtered = new FileSet(getDir(), resolver);
        filtered.setPatternSet(patternSet);
        return filtered;
    }

    public FileSet visit(FileVisitor visitor) {
        DefaultDirectoryWalker walker = new DefaultDirectoryWalker(visitor);
        walker.match(patternSet).start(getDir());
        return this;
    }

    public WorkResult copy(Closure closure) {
        CopyActionImpl action = new FileCopyActionImpl(resolver, new FileCopySpecVisitor());
        action.from(this);
        ConfigureUtil.configure(closure, action);
        action.execute();
        return action;
    }

    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    public FileSet setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    public FileSet setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    public FileSet include(String ... includes) {
        patternSet.include(includes);
        return this;
    }

    public FileSet include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    public FileSet include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public FileSet include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public FileSet exclude(String ... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public FileSet exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public FileSet exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public FileSet exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public boolean contains(File file) {
        String prefix = getDir().getAbsolutePath() + File.separator;
        if (!file.getAbsolutePath().startsWith(prefix)) {
            return false;
        }
        if (!file.isFile()) {
            return false;
        }
        RelativePath path = new RelativePath(true, file.getAbsolutePath().substring(prefix.length()).split(
                Pattern.quote(File.separator)));
        return patternSet.getAsSpec().isSatisfiedBy(new DefaultFileTreeElement(file, path));
    }

    protected void addAsFileSet(Object builder, String nodeName) {
        File dir = getDir();
        if (!dir.exists()) {
            return;
        }
        doAddFileSet(builder, dir, nodeName);
    }

    protected void addAsResourceCollection(Object builder, String nodeName) {
        addAsFileSet(builder, nodeName);
    }

    protected Collection<FileSet> getAsFileSets() {
        return getDir().exists() ? Collections.singletonList(this) : Collections.<FileSet>emptyList();
    }

    protected Object doAddFileSet(Object builder, File dir, String nodeName) {
        new FileSetHelper().addToAntBuilder(builder, dir, patternSet, nodeName);
        return this;
    }
}
