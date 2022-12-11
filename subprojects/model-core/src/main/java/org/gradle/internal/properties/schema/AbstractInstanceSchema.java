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

package org.gradle.internal.properties.schema;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.reflect.validation.ReplayingTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationContext;

public abstract class AbstractInstanceSchema implements InstanceSchema {
    private final ReplayingTypeValidationContext validationProblems;
    private final ImmutableList<NestedPropertySchema> nestedProperties;

    public AbstractInstanceSchema(ReplayingTypeValidationContext validationProblems, ImmutableList<NestedPropertySchema> nestedProperties) {
        this.validationProblems = validationProblems;
        this.nestedProperties = nestedProperties;
    }

    @Override
    public void validate(TypeValidationContext validationContext) {
        validationProblems.replay(null, validationContext);
    }

    @Override
    public ImmutableCollection<NestedPropertySchema> getNestedProperties() {
        return nestedProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractInstanceSchema that = (AbstractInstanceSchema) o;

        return nestedProperties.equals(that.nestedProperties);
    }

    @Override
    public int hashCode() {
        return nestedProperties.hashCode();
    }
}
