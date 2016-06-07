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

import com.google.common.collect.Sets;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.GUtil;

import java.util.Arrays;
import java.util.Set;

public class TaskPropertyFileCollection extends CompositeFileCollection {
    private final String taskName;
    private final String type;
    private final TaskFileInputPropertySpecInternal property;
    private final PathToFileResolver resolver;
    private final Set<Object> files = Sets.newLinkedHashSet();
    private String displayName;

    public TaskPropertyFileCollection(String taskName, String type, TaskFileInputPropertySpecInternal property, PathToFileResolver resolver) {
        this.taskName = taskName;
        this.type = type;
        this.property = property;
        this.resolver = resolver;
    }

    public TaskPropertyFileCollection from(Object... paths) {
        GUtil.addToCollection(files, Arrays.asList(paths));
        return this;
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
        FileCollectionResolveContext nested = context.push(resolver);
        nested.add(files);
    }
}
