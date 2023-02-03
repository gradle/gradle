/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.validation;

import javax.annotation.Nullable;

/**
 * This interface defines methods which shouldn't be called by developers
 * of new validation problems, but only internally used by the validation
 * system, for example to remap nested properties to an owner.
 */
public interface PropertyProblemBuilderInternal extends PropertyProblemBuilder {
    /**
     * This method is called whenever we have nested types, that we're "replaying"
     * validation for those nested types, and that we want the actual property
     * to be reported as the parent.nested property name.
     */
    PropertyProblemBuilder forOwner(@Nullable String parentProperty);

    /**
     * Declares the root type for this property problem
     */
    PropertyProblemBuilder forType(@Nullable Class<?> rootType);
}
