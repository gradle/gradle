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

/**
 * Marks that a property is upgraded
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface UpgradedProperty {
    /**
     * Overrides original type that will be used for generated code. By default, original type is picked from the generic, e.g.
     * Property[T] -> original type becomes T (also Property[Integer] -> Integer and not int)
     * RegularFileProperty -> original type becomes File
     * DirectoryProperty -> original type becomes File
     * MapProperty[K, V] -> original type becomes Map[K, V]
     * ListProperty[T] -> original type becomes List[T]
     * ConfigurableFileCollection -> original type becomes FileCollection
     */
    Class<?> originalType() default DefaultValue.class;

    /**
     * Sets accessors that will be used in instrumentation calls. By default accessors are auto generated,
     * but for special cases you can provide a custom accessors class implementation.
     *
     * There are certain rules to follow:
     * - accessors methods has to be static
     * - accessors first parameter has to be an upgraded class, e.g. when upgrading Checkstyle, first parameter has to be of type Checkstyle
     * - accessors methods has start with access_<old method name>
     * - getter has to be annotated with @UpgradedGetter and setter with @UpgradedSetter
     */
    Class<?> accessors() default DefaultValue.class;

    interface DefaultValue {
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    @interface UpgradedGetter {
        String forProperty();

        /**
         * Tells processor that this getter is used also for Groovy property access.
         * Class can have just one such getter.
         */
        boolean isGetterForGroovyPropertyAccess() default true;
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    @interface UpgradedSetter {
        String forProperty();
    }
}
