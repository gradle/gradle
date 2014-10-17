/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extraction;

public class InvalidManagedModelElementTypeException extends RuntimeException {

    public InvalidManagedModelElementTypeException(Class<?> type, String message) {
        super("Invalid managed model type " + type.getName() + ": " + message);
    }

    public InvalidManagedModelElementTypeException(Class<?> type, String propertyName, InvalidManagedModelElementTypeException cause) {
        super(String.format("Invalid managed model type %s: managed type of property '%s' is invalid", type.getName(), propertyName), cause);
    }
}
