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
import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.schema.DestroysPropertySchema;
import org.gradle.api.internal.tasks.schema.FileOutputPropertySchema;
import org.gradle.api.internal.tasks.schema.LocalStatePropertySchema;
import org.gradle.api.internal.tasks.schema.ServiceReferencePropertySchema;
import org.gradle.api.internal.tasks.schema.TaskInstanceSchema;
import org.gradle.internal.execution.model.FileCollectionResolver;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.model.AbstractPropertyModel;
import org.gradle.internal.model.InstanceModel;

import javax.annotation.Nullable;
import java.io.File;
import java.util.stream.Stream;

/**
 * A view of the inputs of a task.
 *
 * Once created, the view is immutable and registering additional or changing existing task properties will not be detected.
 */
@NonNullApi
public interface TaskOutputModel extends InstanceModel, TaskModel {

    ImmutableCollection<FileOutputPropertyModel> getOutputs();

    ImmutableCollection<LocalStatePropertyModel> getLocalStates();

    ImmutableCollection<DestroysPropertyModel> getDestroys();

    ImmutableCollection<ServiceReferencePropertyModel> getServiceReferences();

    class Extractor implements TaskModel.Extractor<TaskOutputModel> {

        private final FileCollectionResolver fileCollectionResolver;

        public Extractor(FileCollectionResolver fileCollectionResolver) {
            this.fileCollectionResolver = fileCollectionResolver;
        }

        @Override
        public TaskOutputModel extract(TaskInstanceSchema schema) {
            return new DefaultTaskOutputModel(
                schema,
                schema.getOutputs().stream()
                    .flatMap(this::resolveOutputProperties)
                    .collect(ImmutableList.toImmutableList()),
                schema.getLocalStates().stream()
                    .map(this::resolveLocalState)
                    .collect(ImmutableList.toImmutableList()),
                schema.getDestroys().stream()
                    .map(this::resolveDestroys)
                    .collect(ImmutableList.toImmutableList()),
                schema.getServiceReferences().stream()
                    .map(Extractor::resolveServiceReference)
                    .collect(ImmutableList.toImmutableList())
            );
        }

        private Stream<FileOutputPropertyModel> resolveOutputProperties(FileOutputPropertySchema schema) {
            // TODO Resolve composite outputs to multiple properties
            // TODO Replicate OutputUnpacker.resolveOutputFilePropertySpecs() here
            FileCollection files = fileCollectionResolver.resolveFileCollection(schema.getValue());
            if (files == null) {
                return Stream.empty();
            } else {
                return Stream.of(new DefaultFileOutputPropertyModel(schema, files.getSingleFile()));
            }
        }

        private LocalStatePropertyModel resolveLocalState(LocalStatePropertySchema schema) {
            return new DefaultLocalStatePropertyModel(schema, fileCollectionResolver.resolveFileCollection(schema.getValue()));
        }

        private DestroysPropertyModel resolveDestroys(DestroysPropertySchema schema) {
            return new DefaultDestroysPropertyModel(schema, fileCollectionResolver.resolveFileCollection(schema.getValue()));
        }

        private static ServiceReferencePropertyModel resolveServiceReference(ServiceReferencePropertySchema schema) {
            return new DefaultServiceReferencePropertyModel(schema, schema.getValue());
        }

        private static class DefaultFileOutputPropertyModel extends AbstractPropertyModel<File, FileOutputPropertySchema> implements FileOutputPropertyModel {
            public DefaultFileOutputPropertyModel(FileOutputPropertySchema schema, @Nullable File value) {
                super(schema, value);
            }

            @Override
            public TreeType getOutputType() {
                return schema.getOutputType();
            }
        }

        private static class DefaultLocalStatePropertyModel extends AbstractPropertyModel<FileCollection, LocalStatePropertySchema> implements LocalStatePropertyModel {
            public DefaultLocalStatePropertyModel(LocalStatePropertySchema schema, @Nullable FileCollection value) {
                super(schema, value);
            }
        }

        private static class DefaultDestroysPropertyModel extends AbstractPropertyModel<FileCollection, DestroysPropertySchema> implements DestroysPropertyModel {
            public DefaultDestroysPropertyModel(DestroysPropertySchema schema, @Nullable FileCollection value) {
                super(schema, value);
            }
        }

        private static class DefaultServiceReferencePropertyModel extends AbstractPropertyModel<Object, ServiceReferencePropertySchema> implements ServiceReferencePropertyModel {
            public DefaultServiceReferencePropertyModel(ServiceReferencePropertySchema schema, @Nullable Object value) {
                super(schema, value);
            }
        }
    }

    class DefaultTaskOutputModel extends AbstractInstanceModel<TaskInstanceSchema> implements TaskOutputModel {
        private final ImmutableCollection<FileOutputPropertyModel> outputs;
        private final ImmutableCollection<LocalStatePropertyModel> localStates;
        private final ImmutableCollection<DestroysPropertyModel> destroys;
        private final ImmutableCollection<ServiceReferencePropertyModel> serviceReferences;

        public DefaultTaskOutputModel(
            TaskInstanceSchema schema,
            ImmutableCollection<FileOutputPropertyModel> outputs,
            ImmutableCollection<LocalStatePropertyModel> localStates,
            ImmutableCollection<DestroysPropertyModel> destroys,
            ImmutableCollection<ServiceReferencePropertyModel> serviceReferences
        ) {
            super(schema);
            this.outputs = outputs;
            this.localStates = localStates;
            this.destroys = destroys;
            this.serviceReferences = serviceReferences;
        }

        @Override
        public TaskInstanceSchema getSchema() {
            return schema;
        }

        @Override
        public ImmutableCollection<FileOutputPropertyModel> getOutputs() {
            return outputs;
        }

        @Override
        public ImmutableCollection<LocalStatePropertyModel> getLocalStates() {
            return localStates;
        }

        @Override
        public ImmutableCollection<DestroysPropertyModel> getDestroys() {
            return destroys;
        }

        @Override
        public ImmutableCollection<ServiceReferencePropertyModel> getServiceReferences() {
            return serviceReferences;
        }
    }
}
