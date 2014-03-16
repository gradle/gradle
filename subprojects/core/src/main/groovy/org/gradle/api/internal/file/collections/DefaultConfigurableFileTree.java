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
package org.gradle.api.internal.file.collections;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.file.CompositeFileTree;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.FileCopier;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DefaultConfigurableFileTree extends CompositeFileTree implements ConfigurableFileTree {
    private PatternSet patternSet = new PatternSet();
    private Object dir;
    private final FileResolver resolver;
    private final FileCopier fileCopier;
    private final DefaultTaskDependency buildDependency;

    public DefaultConfigurableFileTree(Object dir, FileResolver resolver, TaskResolver taskResolver, FileCopier fileCopier) {
        this(Collections.singletonMap("dir", dir), resolver, taskResolver, fileCopier);
    }

    public DefaultConfigurableFileTree(Map<String, ?> args, FileResolver resolver, TaskResolver taskResolver, FileCopier fileCopier) {
        this.resolver = resolver;
        this.fileCopier = fileCopier;
        ConfigureUtil.configureByMap(args, this);
        buildDependency = new DefaultTaskDependency(taskResolver);
    }

    public PatternSet getPatterns() {
        return patternSet;
    }

    public DefaultConfigurableFileTree setDir(Object dir) {
        from(dir);
        return this;
    }

    public File getDir() {
        if (dir == null) {
            throw new InvalidUserDataException("A base directory must be specified in the task or via a method argument!");
        }
        return resolver.resolve(dir);
    }

    public DefaultConfigurableFileTree from(Object dir) {
        this.dir = dir;
        return this;
    }

    public String getDisplayName() {
        return String.format("directory '%s'", dir);
    }

    public WorkResult copy(final Closure closure) {
        return fileCopier.copy(new Action<CopySpec>() {
            public void execute(CopySpec copySpec) {
                copySpec.from(DefaultConfigurableFileTree.this);
                ConfigureUtil.configure(closure, copySpec);
            }
        });
    }

    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    public DefaultConfigurableFileTree setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    public DefaultConfigurableFileTree setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    public DefaultConfigurableFileTree include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    public DefaultConfigurableFileTree include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    public DefaultConfigurableFileTree include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public DefaultConfigurableFileTree include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public DefaultConfigurableFileTree exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public DefaultConfigurableFileTree exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public DefaultConfigurableFileTree exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public DefaultConfigurableFileTree exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    public void resolve(FileCollectionResolveContext context) {
        File dir = getDir();
        if (!buildDependency.getValues().isEmpty()) {
            context.add(buildDependency);
        }
        context.add(new DirectoryFileTree(dir, patternSet));
    }

    public ConfigurableFileTree builtBy(Object... tasks) {
        buildDependency.add(tasks);
        return this;
    }

    public Set<Object> getBuiltBy() {
        return buildDependency.getValues();
    }

    public ConfigurableFileTree setBuiltBy(Iterable<?> tasks) {
        buildDependency.setValues(tasks);
        return this;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return buildDependency;
    }
}
