/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.model;

import com.google.common.collect.ImmutableCollection;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.schema.TaskInstanceSchema;
import org.gradle.internal.execution.model.FileCollectionResolver;
import org.gradle.internal.execution.model.FileInputPropertyModel;
import org.gradle.internal.execution.model.InputModel;
import org.gradle.internal.execution.model.ScalarInputPropertyModel;
import org.gradle.internal.model.NestedPropertyModel;

/**
 * A view of the inputs of a task.
 *
 * Once created, the view is immutable and registering additional or changing existing task properties will not be detected.
 */
@NonNullApi
public interface TaskInputModel extends InputModel, TaskModel {

    class Extractor extends InputModel.Extractor<TaskInputModel> implements TaskModel.Extractor<TaskInputModel> {

        public Extractor(FileCollectionResolver fileCollectionResolver) {
            super(fileCollectionResolver);
        }

        @Override
        public TaskInputModel extract(TaskInstanceSchema schema) {
            return super.extract(schema, (nested, scalarInputs, fileInputs) -> new DefaultTaskInputModel(
                schema,
                nested,
                scalarInputs,
                fileInputs
            ));
        }
    }

    class DefaultTaskInputModel extends AbstractInputModel<TaskInstanceSchema> implements TaskInputModel {
        public DefaultTaskInputModel(
            TaskInstanceSchema schema,
            ImmutableCollection<NestedPropertyModel> nested,
            ImmutableCollection<ScalarInputPropertyModel> scalarInputs,
            ImmutableCollection<FileInputPropertyModel> fileInputs
        ) {
            super(schema, nested, scalarInputs, fileInputs);
        }

        @Override
        public TaskInstanceSchema getSchema() {
            return schema;
        }
    }
}
