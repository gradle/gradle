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

package org.gradle.api.internal.tasks.schema;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskDestroyablesInternal;
import org.gradle.api.internal.tasks.TaskLocalStateInternal;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.execution.schema.DefaultFileInputPropertySchema;
import org.gradle.internal.execution.schema.DefaultScalarInputPropertySchema;
import org.gradle.internal.execution.schema.FileInputPropertySchema;
import org.gradle.internal.execution.schema.ScalarInputPropertySchema;
import org.gradle.internal.execution.schema.WorkInstanceSchema;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.OutputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.schema.NestedPropertySchema;
import org.gradle.internal.reflect.validation.ReplayingTypeValidationContext;

import javax.annotation.Nullable;

public interface TaskInstanceSchema extends WorkInstanceSchema {
    ImmutableCollection<FileOutputPropertySchema> getOutputs();

    ImmutableCollection<LocalStatePropertySchema> getLocalStates();

    ImmutableCollection<DestroysPropertySchema> getDestroys();

    ImmutableCollection<ServiceReferencePropertySchema> getServiceReferences();

    class Builder extends WorkInstanceSchema.Builder<TaskInstanceSchema> {
        private final ImmutableList.Builder<FileOutputPropertySchema> outputs = ImmutableList.builder();
        private final ImmutableList.Builder<LocalStatePropertySchema> localStates = ImmutableList.builder();
        private final ImmutableList.Builder<DestroysPropertySchema> destroys = ImmutableList.builder();
        private final ImmutableList.Builder<ServiceReferencePropertySchema> serviceReferences = ImmutableList.builder();

        public Builder(TaskInternal task) {
            // TODO We should turn this around and keep track of registered properties here perhaps
            task.getInputs().visitRegisteredProperties(new RegisteredPropertyVisitor());
            task.getOutputs().visitRegisteredProperties(new RegisteredPropertyVisitor());
            ((TaskDestroyablesInternal) task.getDestroyables()).visitRegisteredProperties(new RegisteredPropertyVisitor());
            ((TaskLocalStateInternal) task.getLocalState()).visitRegisteredProperties(new RegisteredPropertyVisitor());
            // TODO How do we visit require services?
        }

        private class RegisteredPropertyVisitor implements PropertyVisitor {
            private int destroysCounter = 0;
            private int localStateCounter = 0;

            @Override
            public void visitInputFileProperty(String propertyName, boolean optional, InputBehavior behavior, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity, @Nullable FileNormalizer fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                add(new DefaultFileInputPropertySchema(
                    propertyName,
                    optional,
                    fileNormalizer == null ? InputNormalizer.ABSOLUTE_PATH : fileNormalizer,
                    behavior,
                    directorySensitivity,
                    lineEndingSensitivity,
                    // TODO These should carry build dependencies with them
                    value::call
                ));
            }

            @Override
            public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
                add(new DefaultScalarInputPropertySchema(
                    propertyName,
                    optional,
                    value::call
                ));
            }

            @Override
            public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
                add(new DefaultFileOutputPropertySchema(
                    propertyName,
                    optional,
                    filePropertyType.getOutputType(),
                    value::call
                ));
            }

            @Override
            public void visitServiceReference(String propertyName, boolean optional, PropertyValue value, @Nullable String serviceName) {
                add(new DefaultServiceReferencePropertySchema(
                    propertyName,
                    optional,
                    serviceName,
                    value::call
                ));
            }

            @Override
            public void visitDestroyableProperty(Object value) {
                add(new DefaultDestroysPropertySchema("destroys$" + destroysCounter++, false, () -> value));
            }

            @Override
            public void visitLocalStateProperty(Object value) {
                add(new DefaultLocalStatePropertySchema("localState" + localStateCounter++, false, () -> value));
            }
        }

        public void add(FileOutputPropertySchema property) {
            outputs.add(property);
        }

        public void add(LocalStatePropertySchema property) {
            localStates.add(property);
        }

        public void add(DestroysPropertySchema property) {
            destroys.add(property);
        }

        public void add(ServiceReferencePropertySchema property) {
            serviceReferences.add(property);
        }

        @Override
        protected TaskInstanceSchema build(
            ReplayingTypeValidationContext validationProblems,
            ImmutableList<NestedPropertySchema> nestedProperties,
            ImmutableList<ScalarInputPropertySchema> scalarInputs,
            ImmutableList<FileInputPropertySchema> fileInputs
        ) {
            return new DefaultTaskInstanceSchema(
                validationProblems,
                nestedProperties,
                scalarInputs,
                fileInputs,
                toSortedList(outputs),
                toSortedList(localStates),
                toSortedList(destroys),
                toSortedList(serviceReferences)
            );
        }
    }
}
