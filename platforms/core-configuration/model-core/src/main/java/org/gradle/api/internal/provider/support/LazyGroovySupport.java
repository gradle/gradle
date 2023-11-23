/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider.support;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.internal.instantiation.generator.AsmBackedClassGenerator;

/**
 * An interface used to support lazy assignment for types like {@link Property} and {@link ConfigurableFileCollection} in Groovy DSL.
 * Call to this interface is generated via {@link AsmBackedClassGenerator}.
 */
public interface LazyGroovySupport {

    /**
     * Sets the value from some arbitrary object. Used from the Groovy DSL.
     */
    void setFromAnyValue(Object object);
}
