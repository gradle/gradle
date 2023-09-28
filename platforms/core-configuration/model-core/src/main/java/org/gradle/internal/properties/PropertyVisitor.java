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

package org.gradle.internal.properties;

import org.gradle.api.services.BuildService;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;

import javax.annotation.Nullable;

/**
 * Visits properties of beans which are inputs, outputs, destroyables, local state or service references.
 */
public interface PropertyVisitor {
    default void visitInputFileProperty(String propertyName, boolean optional, InputBehavior behavior, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity, @Nullable FileNormalizer fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {}

    default void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {}

    default void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {}

    default void visitDestroyableProperty(Object value) {}

    default void visitLocalStateProperty(Object value) {}

    /**
     * Visits a service reference. Service references may or may not be declared with a name.
     */
    default void visitServiceReference(String propertyName, boolean optional, PropertyValue value, @Nullable String serviceName, Class<? extends BuildService<?>> buildServiceType) {}
}
