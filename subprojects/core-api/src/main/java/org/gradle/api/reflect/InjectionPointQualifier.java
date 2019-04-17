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

package org.gradle.api.reflect;

import org.gradle.api.Incubating;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated annotation can be used to inject elements of the supported types.
 *
 * <p>If both {@link #supportedTypes()} and {@link #supportedProviderTypes()} are empty, all types are supported.</p>
 *
 * @since 5.3
 */
@Incubating
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
@Documented
public @interface InjectionPointQualifier {
    /**
     * The types which are supported for injection.
     */
    Class<?>[] supportedTypes() default {};

    /**
     * The types of {@link org.gradle.api.provider.Provider}s supported for injection.
     * <br>
     * If e.g. {@link org.gradle.api.file.FileSystemLocation} is in the list, then this annotation can be used for injecting {@link org.gradle.api.provider.Provider}&lt;{@link org.gradle.api.file.FileSystemLocation}&gt;.
     *
     * @since 5.5
     */
    Class<?>[] supportedProviderTypes() default {};
}
