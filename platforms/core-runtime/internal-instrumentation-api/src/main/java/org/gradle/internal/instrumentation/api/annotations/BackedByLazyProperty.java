/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.instrumentation.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an eager getter whose value is stored in a lazy property.
 *
 * <p>Gradle reads the value through the getter, but takes task dependencies,
 * finalization and convention mapping from the lazy property.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface BackedByLazyProperty {
    /**
     * A dot-separated bean property path to the lazy property, relative to the object that declares the getter,
     * for example {@code "lazyProperties.sourceCompatibility"}.
     *
     * The path must end at a {@code Provider} or a configurable value such as a {@code ConfigurableFileCollection}.
     */
    String value();
}
