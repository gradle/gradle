/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;

public class TaskPropertyFileCollection extends CompositeFileCollection {
    private final String taskName;
    private final String type;
    private final TaskFilePropertySpec property;
    private final FileResolver resolver;
    private final Object paths;
    private String displayName;

    public TaskPropertyFileCollection(String taskName, String type, TaskFilePropertySpec property, FileResolver resolver, Object paths) {
        this.taskName = taskName;
        this.type = type;
        this.property = property;
        this.resolver = resolver;
        this.paths = paths;
    }

    public Object getPaths() {
        return paths;
    }

    @Override
    public String getDisplayName() {
        if (displayName == null) {
            displayName = type + " files for task '" + taskName + "' property '" + property.getPropertyName() + "'";
        }
        return displayName;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.push(resolver).add(paths);
    }
}
