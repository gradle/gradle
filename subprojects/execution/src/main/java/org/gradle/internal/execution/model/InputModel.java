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

package org.gradle.internal.execution.model;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.execution.schema.FileInputPropertySchema;
import org.gradle.internal.execution.schema.ScalarInputPropertySchema;
import org.gradle.internal.execution.schema.WorkInstanceSchema;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.model.AbstractPropertyModel;
import org.gradle.internal.model.InstanceModel;
import org.gradle.internal.model.NestedPropertyModel;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.schema.NestedPropertySchema;

import javax.annotation.Nullable;

/**
 * A view of the inputs of a unit of work.
 */
public interface InputModel extends InstanceModel {
    ImmutableCollection<NestedPropertyModel> getNested();

    ImmutableCollection<ScalarInputPropertyModel> getScalarInputs();

    ImmutableCollection<FileInputPropertyModel> getFileInputs();

    abstract class Extractor<M extends InstanceModel> {
        private final FileCollectionResolver fileCollectionResolver;

        public Extractor(FileCollectionResolver fileCollectionResolver) {
            this.fileCollectionResolver = fileCollectionResolver;
        }

        protected M extract(WorkInstanceSchema schema, Builder<M> builder) {
            return builder.build(
                schema.getNestedProperties().stream()
                    .map(DefaultNestedPropertyModel::new)
                    .collect(ImmutableList.toImmutableList()),
                schema.getScalarInputs().stream()
                    .map(DefaultScalarInputPropertyModel::new)
                    .collect(ImmutableList.toImmutableList()),
                schema.getFileInputs().stream()
                    .map(this::newFileInputProperty)
                    .collect(ImmutableList.toImmutableList())
            );
        }

        private DefaultFileInputPropertyModel newFileInputProperty(FileInputPropertySchema propertySchema) {
            return new DefaultFileInputPropertyModel(propertySchema, fileCollectionResolver.resolveFileCollection(propertySchema.getValue()));
        }

        protected interface Builder<M extends InstanceModel> {
            M build(
                ImmutableList<NestedPropertyModel> nestedPropertyModels,
                ImmutableCollection<ScalarInputPropertyModel> scalarInputs,
                ImmutableCollection<FileInputPropertyModel> fileInputs
            );
        }

        private static class DefaultNestedPropertyModel extends AbstractPropertyModel<Object, NestedPropertySchema> implements NestedPropertyModel {
            public DefaultNestedPropertyModel(NestedPropertySchema schema) {
                super(schema, schema.getValue());
            }
        }

        private static class DefaultScalarInputPropertyModel extends AbstractPropertyModel<Object, ScalarInputPropertySchema> implements ScalarInputPropertyModel {
            public DefaultScalarInputPropertyModel(ScalarInputPropertySchema schema) {
                super(schema, schema.getValue());
            }
        }

        private static class DefaultFileInputPropertyModel extends AbstractPropertyModel<FileCollection, FileInputPropertySchema> implements FileInputPropertyModel {
            public DefaultFileInputPropertyModel(FileInputPropertySchema schema, @Nullable FileCollection value) {
                super(schema, value);
            }

            @Override
            public InputBehavior getBehavior() {
                return schema.getBehavior();
            }

            @Override
            public DirectorySensitivity getDirectorySensitivity() {
                return schema.getDirectorySensitivity();
            }

            @Override
            public LineEndingSensitivity getLineEndingNormalization() {
                return schema.getLineEndingSensitivity();
            }

            @Nullable
            @Override
            public FileNormalizer getNormalizer() {
                return schema.getNormalizer();
            }
        }
    }

    abstract class AbstractInputModel<S extends WorkInstanceSchema> extends AbstractInstanceModel<S> implements InputModel {
        private final ImmutableCollection<NestedPropertyModel> nested;
        private final ImmutableCollection<ScalarInputPropertyModel> scalarInputs;
        private final ImmutableCollection<FileInputPropertyModel> fileInputs;

        public AbstractInputModel(
            S schema,
            ImmutableCollection<NestedPropertyModel> nested,
            ImmutableCollection<ScalarInputPropertyModel> scalarInputs,
            ImmutableCollection<FileInputPropertyModel> fileInputs
        ) {
            super(schema);
            this.nested = nested;
            this.scalarInputs = scalarInputs;
            this.fileInputs = fileInputs;
        }


        @Override
        public ImmutableCollection<NestedPropertyModel> getNested() {
            return nested;
        }

        @Override
        public ImmutableCollection<ScalarInputPropertyModel> getScalarInputs() {
            return scalarInputs;
        }

        @Override
        public ImmutableCollection<FileInputPropertyModel> getFileInputs() {
            return fileInputs;
        }
    }
}
