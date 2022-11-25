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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.execution.model.InputFilePropertyModel;
import org.gradle.internal.execution.model.InputModel;
import org.gradle.internal.execution.model.InputPropertyModel;
import org.gradle.internal.execution.model.impl.InputDirectoryPropertyModelBuilder;
import org.gradle.internal.execution.model.impl.InputFilePropertyModelBuilder;
import org.gradle.internal.execution.model.impl.InputFilesPropertyModelBuilder;
import org.gradle.internal.execution.model.impl.InputModelBuilder;
import org.gradle.internal.execution.model.impl.InputPropertyModelBuilder;
import org.gradle.internal.model.PropertyModelBuilder;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.schema.InstanceSchema;
import org.gradle.internal.schema.InstanceSchemaExtractor;

import java.lang.annotation.Annotation;
import java.util.function.Function;
import java.util.stream.Stream;

public class DefaultTaskInputModelBuilder implements TaskInputModelBuilder {
    private final InstanceSchemaExtractor schemaExtractor;
    private final ImmutableMap<Class<? extends Annotation>, PropertyModelBuilder<? extends Annotation, InputPropertyModel>> propertyModelBuilders;
    private final ImmutableMap<Class<? extends Annotation>, PropertyModelBuilder<? extends Annotation, InputFilePropertyModel>> filePropertyModelBuilders;

    public DefaultTaskInputModelBuilder(InstanceSchemaExtractor schemaExtractor, FileCollectionFactory fileCollectionFactory) {
        this.propertyModelBuilders = Stream.of(new InputPropertyModelBuilder())
            .collect(ImmutableMap.toImmutableMap(
                PropertyModelBuilder::getHandledPropertyType,
                Function.identity()
            ));
        this.filePropertyModelBuilders = Stream.of(
            new InputFilePropertyModelBuilder(fileCollectionFactory::resolving),
            new InputFilesPropertyModelBuilder(fileCollectionFactory::resolving),
            new InputDirectoryPropertyModelBuilder(fileCollectionFactory::resolving)
        ).collect(ImmutableMap.toImmutableMap(
            PropertyModelBuilder::getHandledPropertyType,
            Function.identity()
        ));
        this.schemaExtractor = schemaExtractor;
    }

    @Override
    public InputModel buildModelFrom(Task task, TypeValidationContext validationContext) {
        // TODO The schema should come from the task instance itself
        InstanceSchema instanceSchema = schemaExtractor.extractSchema(task, validationContext);
        InputModelBuilder builder = new InputModelBuilder();

        // Collect properties
        instanceSchema.properties().forEach(schema -> {
            PropertyModelBuilder<? extends Annotation, InputPropertyModel> propertyBuilder = propertyModelBuilders.get(schema.getMetadata().getPropertyType());
            if (propertyBuilder != null) {
                builder.addInputProperty(propertyBuilder.getModel(schema));
            }
        });

        // Collect file properties
        instanceSchema.properties().forEach(schema -> {
            PropertyModelBuilder<? extends Annotation, InputFilePropertyModel> propertyBuilder = filePropertyModelBuilders.get(schema.getMetadata().getPropertyType());
            if (propertyBuilder != null) {
                builder.addInputFilePropertyModel(propertyBuilder.getModel(schema));
            }
        });
        return builder.buildModel();
    }
}
