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

package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A {@code SourceTask} performs some operation on source files.
 */
@NonNullApi
public class SourceTask extends ConventionTask implements PatternFilterable {
    private final List<Object> source = new ArrayList<Object>();
    private final PatternFilterable patternSet;

    public SourceTask() {
        patternSet = getPatternSetFactory().create();
    }

    @Inject
    protected Factory<PatternSet> getPatternSetFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the source for this task, after the include and exclude patterns have been applied. Ignores source files which do not exist.
     *
     * <p>
     * The {@link PathSensitivity} for the sources is configured to be {@link PathSensitivity#ABSOLUTE}.
     * If your sources are less strict, please change it accordingly by overriding this method in your subclass.
     * </p>
     *
     * @return The source.
     */
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileTree getSource() {
        ArrayList<Object> copy = new ArrayList<Object>(this.source);
        FileTree src = getProject().files(copy).getAsFileTree();
        return src.matching(patternSet);
    }

    /**
     * Sets the source for this task.
     *
     * @param source The source.
     * @since 4.0
     */
    public void setSource(FileTree source) {
        setSource((Object) source);
    }

    /**
     * Sets the source for this task. The given source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param source The source.
     */
    public void setSource(Object source) {
        this.source.clear();
        this.source.add(source);
    }

    /**
     * Adds some source to this task. The given source objects will be evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param sources The source to add
     * @return this
     */
    public SourceTask source(Object... sources) {
        Collections.addAll(this.source, sources);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }
}
