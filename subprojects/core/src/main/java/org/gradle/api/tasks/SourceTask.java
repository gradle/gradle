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
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.collections.ConfigurableFileTreeInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import java.util.Set;

/**
 * A {@code SourceTask} performs some operation on source files.
 */
@NonNullApi
@DisableCachingByDefault(because = "Super-class, not to be instantiated directly")
public abstract class SourceTask extends ConventionTask implements PatternFilterable {
    @Internal
    protected PatternFilterable getPatternSet() {
        // TODO Find a better way to expose the pattern set
        return ((ConfigurableFileTreeInternal) getSource()).getPatternSet();
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
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileTree getSource();

//    /**
//     * Sets the source for this task.
//     *
//     * @param source The source.
//     * @since 4.0
//     */
//    public void setSource(FileTree source) {
//        setSource((Object) source);
//    }
//
//    /**
//     * Sets the source for this task. The given source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
//     *
//     * @param source The source.
//     */
//    public void setSource(Object source) {
//        getSource().setFrom(source);
//    }

    /**
     * Adds some source to this task. The given source objects will be evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param sources The source to add
     * @return this
     */
    public SourceTask source(Object... sources) {
        getSource().from(sources);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask include(String... includes) {
        getSource().include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask include(Iterable<String> includes) {
        getSource().include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask include(Spec<FileTreeElement> includeSpec) {
        getSource().include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask include(Closure includeSpec) {
        getSource().include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask exclude(String... excludes) {
        getSource().exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask exclude(Iterable<String> excludes) {
        getSource().exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask exclude(Spec<FileTreeElement> excludeSpec) {
        getSource().exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask exclude(Closure excludeSpec) {
        getSource().exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    @ToBeReplacedByLazyProperty
    public Set<String> getIncludes() {
        return getSource().getIncludes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask setIncludes(Iterable<String> includes) {
        getSource().setIncludes(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    @ToBeReplacedByLazyProperty
    public Set<String> getExcludes() {
        return getSource().getExcludes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceTask setExcludes(Iterable<String> excludes) {
        getSource().setExcludes(excludes);
        return this;
    }
}
