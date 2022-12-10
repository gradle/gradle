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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.Task;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.execution.model.InputModel;
import org.gradle.internal.execution.model.impl.InputDirectoryPropertyModelBuilder;
import org.gradle.internal.execution.model.impl.InputFilePropertyModelBuilder;
import org.gradle.internal.execution.model.impl.InputFilesPropertyModelBuilder;
import org.gradle.internal.execution.model.impl.InputModelBuilderVisitor;
import org.gradle.internal.execution.model.impl.InputPropertyModelBuilder;
import org.gradle.internal.model.AbstractInstanceModelBuilder;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.schema.InstanceSchema;
import org.gradle.internal.schema.InstanceSchemaExtractor;

public class DefaultTaskInputModelBuilder extends AbstractInstanceModelBuilder<InputModelBuilderVisitor> implements TaskInputModelBuilder {
    private final InstanceSchemaExtractor schemaExtractor;

    public DefaultTaskInputModelBuilder(InstanceSchemaExtractor schemaExtractor, FileCollectionFactory fileCollectionFactory) {
        super(
            new InputPropertyModelBuilder(),
            new InputFilePropertyModelBuilder(fileCollectionFactory::resolving),
            new InputFilesPropertyModelBuilder(fileCollectionFactory::resolving),
            new InputDirectoryPropertyModelBuilder(fileCollectionFactory::resolving)
        );
        this.schemaExtractor = schemaExtractor;
    }

    @Override
    public InputModel buildModelFrom(Task task, TypeValidationContext validationContext) {
        // TODO The schema should come from the task instance itself
        InstanceSchema schema = schemaExtractor.extractSchema(task, validationContext);
        InputModelBuilderVisitor visitor = new InputModelBuilderVisitor();
        handleProperties(schema, visitor);
        return visitor.buildModel();
    }
}
