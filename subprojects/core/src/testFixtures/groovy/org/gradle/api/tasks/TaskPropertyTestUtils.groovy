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

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.CompositeFileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskPropertyUtils
import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor
import org.gradle.api.internal.tasks.properties.GetInputPropertiesVisitor
import org.gradle.internal.properties.bean.PropertyWalker

import java.util.function.Consumer

class TaskPropertyTestUtils {
    static Map<String, Object> getProperties(DefaultTask task) {
        getProperties(task, task.getServices().get(PropertyWalker))
    }

    static Map<String, Object> getProperties(TaskInternal task, PropertyWalker propertyWalker) {
        GetInputPropertiesVisitor visitor = new GetInputPropertiesVisitor()
        TaskPropertyUtils.visitProperties(propertyWalker, task, visitor)
        return visitor.getProperties().collectEntries { [it.propertyName, it.value.call()] }
    }

    static FileCollection getInputFiles(DefaultTask task) {
        def fileCollectionFactory = task.getServices().get(FileCollectionFactory)
        GetInputFilesVisitor visitor = new GetInputFilesVisitor(task.toString(), fileCollectionFactory)
        def walker = task.getServices().get(PropertyWalker)
        TaskPropertyUtils.visitProperties(walker, task, visitor)
        return new CompositeFileCollection() {
            @Override
            String getDisplayName() {
                return task + " input files"
            }

            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> consumer) {
                for (def filePropertySpec : visitor.fileProperties) {
                    consumer.accept(filePropertySpec.getPropertyFiles())
                }
            }
        }
    }
}
