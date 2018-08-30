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

package org.gradle.api.internal.file.collections;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link org.gradle.api.internal.file.FileResolver}.
 */
public class DefaultConfigurableFileCollection extends CompositeFileCollection implements ConfigurableFileCollection {
    private final Set<Object> files;
    private final String displayName;
    private final PathToFileResolver resolver;
    private final DefaultTaskDependency buildDependency;

    public DefaultConfigurableFileCollection(PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver) {
        this("file collection", fileResolver, taskResolver, null);
    }

    public DefaultConfigurableFileCollection(PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver, Collection<?> files) {
        this("file collection", fileResolver, taskResolver, files);
    }

    public DefaultConfigurableFileCollection(PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver, Object[] files) {
        this("file collection", fileResolver, taskResolver, Arrays.asList(files));
    }

    public DefaultConfigurableFileCollection(String displayName, PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver) {
        this(displayName, fileResolver, taskResolver, null);
    }

    public DefaultConfigurableFileCollection(String displayName, PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver, @Nullable Collection<?> files) {
        this.displayName = displayName;
        this.resolver = fileResolver;
        this.files = new LinkedHashSet<Object>();
        if (files != null) {
            this.files.addAll(files);
        }
        buildDependency = new DefaultTaskDependency(taskResolver);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Set<Object> getFrom() {
        return files;
    }

    @Override
    public void setFrom(Iterable<?> path) {
        files.clear();
        files.add(path);
    }

    @Override
    public void setFrom(Object... paths) {
        files.clear();
        GUtil.addToCollection(files, Arrays.asList(paths));
    }

    @Override
    public ConfigurableFileCollection from(Object... paths) {
        GUtil.addToCollection(files, Arrays.asList(paths));
        return this;
    }

    @Override
    public ConfigurableFileCollection builtBy(Object... tasks) {
        buildDependency.add(tasks);
        return this;
    }

    @Override
    public Set<Object> getBuiltBy() {
        return buildDependency.getMutableValues();
    }

    @Override
    public ConfigurableFileCollection setBuiltBy(Iterable<?> tasks) {
        buildDependency.setValues(tasks);
        return this;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        FileCollectionResolveContext nested = context.push(resolver);
        nested.add(files);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(buildDependency);
        super.visitDependencies(context);
    }
}
