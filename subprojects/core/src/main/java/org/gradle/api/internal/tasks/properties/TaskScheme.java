/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.schema.TaskInstanceSchema;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.schema.InstanceSchemaExtractor;

public class TaskScheme implements TypeScheme {
    private final InstantiationScheme instantiationScheme;
    private final InspectionScheme inspectionScheme;
    private final InstanceSchemaExtractor<TaskInternal, TaskInstanceSchema> instanceSchemaExtractor;

    public TaskScheme(InstantiationScheme instantiationScheme, InspectionScheme inspectionScheme, InstanceSchemaExtractor<TaskInternal, TaskInstanceSchema> instanceSchemaExtractor) {
        this.instantiationScheme = instantiationScheme;
        this.inspectionScheme = inspectionScheme;
        this.instanceSchemaExtractor = instanceSchemaExtractor;
    }

    @Override
    public TypeMetadataStore getMetadataStore() {
        return inspectionScheme.getMetadataStore();
    }

    @Override
    public boolean appliesTo(Class<?> type) {
        return Task.class.isAssignableFrom(type);
    }

    public InstantiationScheme getInstantiationScheme() {
        return instantiationScheme;
    }

    public InspectionScheme getInspectionScheme() {
        return inspectionScheme;
    }

    public InstanceSchemaExtractor<TaskInternal, TaskInstanceSchema> getInstanceSchemaExtractor() {
        return instanceSchemaExtractor;
    }
}
