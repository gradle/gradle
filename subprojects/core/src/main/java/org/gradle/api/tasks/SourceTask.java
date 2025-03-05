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
import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.LazyPatternFilterable;
import org.gradle.internal.Factory;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.SETTER;

/**
 * A {@code SourceTask} performs some operation on source files.
 */
@NonNullApi
@DisableCachingByDefault(because = "Super-class, not to be instantiated directly")
public abstract class SourceTask extends ConventionTask {
    private ConfigurableFileCollection sourceFiles = getProject().getObjects().fileCollection();
    private final LazyPatternFilterable patternFilterable;

    public SourceTask() {
        patternFilterable = getProject().getObjects().newInstance(LazyPatternFilterable.class);
    }

    @Inject
    protected Factory<PatternSet> getPatternSetFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is called by the task implementation to apply the include and exclude patterns to the source files.
     *
     * @since 9.0
     */
    @Incubating
    protected void applyPatternSetTo(PatternFilterable patternFilterable) {
        this.patternFilterable.applyTo(patternFilterable);
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
    @ToBeReplacedByLazyProperty
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileTree getSource() {
        return sourceFiles.getAsFileTree().matching(this::applyPatternSetTo);
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
        sourceFiles = getProject().getObjects().fileCollection().from(source);
    }

    /**
     * Adds some source to this task. The given source objects will be evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param sources The source to add
     * @return this
     */
    public SourceTask source(Object... sources) {
        sourceFiles.from(sources);
        return this;
    }

    /**
     * Adds to the set of include patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     * @see PatternFilterable#include(String...)
     */
    public SourceTask include(String... includes) {
        patternFilterable.include(includes);
        return this;
    }

    /**
     * Adds to the set of include patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     * @see PatternFilterable#include(Iterable)
     */
    public SourceTask include(Iterable<String> includes) {
        patternFilterable.include(includes);
        return this;
    }

    /**
     * Adds to the set of include patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     * @see PatternFilterable#include(Spec)
     */
    public SourceTask include(Spec<FileTreeElement> includeSpec) {
        patternFilterable.include(includeSpec);
        return this;
    }

    /**
     * Adds to the set of include patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     * @see PatternFilterable#include(Closure)
     */
    public SourceTask include(Closure includeSpec) {
        patternFilterable.include(includeSpec);
        return this;
    }

    /**
     * Adds to the set of exclude patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     * @see PatternFilterable#exclude(String...)
     */
    public SourceTask exclude(String... excludes) {
        patternFilterable.exclude(excludes);
        return this;
    }

    /**
     * Adds to the set of exclude patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     * @see PatternFilterable#exclude(Iterable)
     */
    public SourceTask exclude(Iterable<String> excludes) {
        patternFilterable.exclude(excludes);
        return this;
    }

    /**
     * Adds to the set of exclude patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     * @see PatternFilterable#exclude(Spec)
     */
    public SourceTask exclude(Spec<FileTreeElement> excludeSpec) {
        patternFilterable.exclude(excludeSpec);
        return this;
    }

    /**
     * Adds to the set of exclude patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     * @see PatternFilterable#exclude(Closure)
     */
    public SourceTask exclude(Closure excludeSpec) {
        patternFilterable.exclude(excludeSpec);
        return this;
    }

    /**
     * The set of include patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Internal
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = GETTER, name = "getIncludes"),
        @ReplacedAccessor(value = SETTER, name = "setIncludes", originalType = Iterable.class, fluentSetter = true)
    })
    public SetProperty<String> getIncludes() {
        return patternFilterable.getIncludes();
    }

    /**
     * The set of exclude patterns.
     *
     * @see org.gradle.api.tasks.util.PatternFilterable Pattern Format
     */
    @Internal
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = GETTER, name = "getExcludes"),
        @ReplacedAccessor(value = SETTER, name = "setExcludes", originalType = Iterable.class, fluentSetter = true)
    })
    public SetProperty<String> getExcludes() {
        return patternFilterable.getExcludes();
    }
}
