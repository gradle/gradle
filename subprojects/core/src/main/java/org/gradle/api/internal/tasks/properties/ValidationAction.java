/*
 * Copyright 2017 the original author or authors.
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

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * An action that validates property values.
 */
public interface ValidationAction {
    /**
     * Validates the given property value according to some rule.
     *
     * @param propertyName the name of the property being validated
     * @param value a supplier of a non-null value - side effects are guaranteed to happen only once
     * @param context the context in which the validation is being performed
     */
    void validate(String propertyName, @Nonnull Supplier<Object> value, PropertyValidationContext context);
}
