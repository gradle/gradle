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

package org.gradle.internal.instrumentation.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility.ACCESSORS_REMOVED;

/**
 * Marks that a property replaces an eager property.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface ReplacesEagerProperty {
    /**
     * Overrides original type that will be used for generated code.
     * By default, the original type is determined from the lazy property type, e.g.:
     * Property[T] -> original type becomes T (also Property[Integer] -> Integer and not int)
     * RegularFileProperty -> original type becomes File
     * DirectoryProperty -> original type becomes File
     * MapProperty[K, V] -> original type becomes Map[K, V]
     * ListProperty[T] -> original type becomes List[T]
     * ConfigurableFileCollection -> original type becomes FileCollection
     */
    Class<?> originalType() default DefaultValue.class;

    /**
     * Whether the setter accessor for property was fluent
     */
    boolean fluentSetter() default false;

    /**
     * Configuration for binary compatibility check, see {@link BinaryCompatibility}
     */
    BinaryCompatibility binaryCompatibility() default ACCESSORS_REMOVED;

    /**
     * Accessors that are replaced by the property
     */
    ReplacedAccessor[] replacedAccessors() default {};

    /**
     * Deprecation configuration for the replaced accessors
     */
    ReplacedDeprecation deprecation() default @ReplacedDeprecation();

    interface DefaultValue {
    }

    enum BinaryCompatibility {
        /**
         * Gradle binary compatibility check will fail if the accessor was not removed
         */
        ACCESSORS_REMOVED,

        /**
         * Gradle binary compatibility check will fail if the accessor was not kept
         */
        ACCESSORS_KEPT
    }
}
