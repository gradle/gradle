/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.binding;

import org.gradle.api.GradleException;
import org.gradle.model.internal.type.ModelType;

public class InvalidManagedPropertyException extends GradleException {
    public InvalidManagedPropertyException(ModelType<?> type, String propertyName, String message) {
        super(getMessage(type, propertyName, message));
    }

    public InvalidManagedPropertyException(ModelType<?> type, String propertyName, String message, Throwable cause) {
        super(getMessage(type, propertyName, message), cause);
    }

    private static String getMessage(ModelType<?> type, String propertyName, String message) {
        return String.format("Property '%s' of type '%s' %s.", propertyName, type.getDisplayName(), message);
    }
}
