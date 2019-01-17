/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.CompositeFileCollection
import org.gradle.api.internal.file.collections.FileCollectionResolveContext
import org.gradle.api.internal.tasks.TaskPropertyUtils
import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor
import org.gradle.api.internal.tasks.properties.GetInputPropertiesVisitor
import org.gradle.api.internal.tasks.properties.PropertyWalker
import org.gradle.internal.file.PathToFileResolver

class TaskPropertyTestUtils {
    static Map<String, Object> getProperties(AbstractTask task) {
        getProperties(task, task.getServices().get(PropertyWalker))
    }

    static Map<String, Object> getProperties(TaskInternal task, PropertyWalker propertyWalker) {
        GetInputPropertiesVisitor visitor = new GetInputPropertiesVisitor(task.getName())
        TaskPropertyUtils.visitProperties(propertyWalker, task, visitor)
        //noinspection ConstantConditions
        return visitor.getPropertyValuesFactory().create()
    }

    static FileCollection getInputFiles(AbstractTask task) {
        def resolver = task.getServices().get(PathToFileResolver)
        GetInputFilesVisitor visitor = new GetInputFilesVisitor(task.toString(), resolver)
        def walker = task.getServices().get(PropertyWalker)
        TaskPropertyUtils.visitProperties(walker, task, visitor)
        return new CompositeFileCollection() {
            @Override
            String getDisplayName() {
                return task + " input files"
            }

            @Override
            void visitContents(FileCollectionResolveContext context) {
                for (def filePropertySpec : visitor.fileProperties) {
                    context.add(filePropertySpec.getPropertyFiles())
                }
            }
        }
    }
}
