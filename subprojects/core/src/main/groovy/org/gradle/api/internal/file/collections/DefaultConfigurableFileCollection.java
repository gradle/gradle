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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link org.gradle.api.internal.file.FileResolver}.
 */
public class DefaultConfigurableFileCollection extends CompositeFileCollection implements ConfigurableFileCollection {
    private final List<Object> files;
    private final String displayName;
    private final FileResolver resolver;
    private final DefaultTaskDependency buildDependency;

    public DefaultConfigurableFileCollection(FileResolver fileResolver, TaskResolver taskResolver, Object... files) {
        this("file collection", fileResolver, taskResolver, files);
    }

    public DefaultConfigurableFileCollection(String displayName, FileResolver fileResolver, TaskResolver taskResolver, Object... files) {
        this.displayName = displayName;
        this.resolver = fileResolver;
        this.files = new ArrayList<Object>(Arrays.asList(files));
        buildDependency = new DefaultTaskDependency(taskResolver);
    }

    public DefaultConfigurableFileCollection clear() {
        files.clear();
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<?> getSources() {
        return files;
    }

    public ConfigurableFileCollection from(Object... paths) {
        files.add(paths);
        return this;
    }

    public ConfigurableFileCollection builtBy(Object... tasks) {
        buildDependency.add(tasks);
        return this;
    }

    public Set<Object> getBuiltBy() {
        return buildDependency.getValues();
    }

    public ConfigurableFileCollection setBuiltBy(Iterable<?> tasks) {
        buildDependency.setValues(tasks);
        return this;
    }

    @Override
    protected void resolve(FileCollectionResolveContext context) {
        FileCollectionResolveContext nested = context.push(resolver);
        nested.add(buildDependency);
        nested.add(files);
    }
}
