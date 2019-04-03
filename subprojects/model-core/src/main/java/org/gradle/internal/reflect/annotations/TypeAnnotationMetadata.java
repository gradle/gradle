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

package org.gradle.internal.reflect.annotations;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.reflect.ParameterValidationContext;

import javax.annotation.Nullable;

public interface TypeAnnotationMetadata {
    /**
     * Returns information about each property of the type and the annotations on the property.
     */
    ImmutableSortedSet<PropertyAnnotationMetadata> getPropertiesAnnotationMetadata();

    void visitValidationFailures(@Nullable String ownerPath, ParameterValidationContext validationContext);
}
