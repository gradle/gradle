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

package org.gradle.api;

import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or a whole package as providing a non-null API by default.
 *
 * All parameter and return types are assumed to be {@link javax.annotation.Nonnull} unless specifically marked as {@link javax.annotation.Nullable}.
 *
 * All types of an annotated package inherit the package rule.
 * Subpackages do not inherit nullability rules and must be annotated.
 *
 * @since 4.2
 * @deprecated Deprecated in Gradle 9.0 for removal in Gradle 10.
 * Prefer JSpecify annotations such as {@link org.jspecify.annotations.NullMarked} and {@link org.jspecify.annotations.Nullable}.
 * Note that you can also still use JSR305 annotations such as {@link javax.annotation.Nonnull} and {@link javax.annotation.Nullable}.
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@org.jetbrains.annotations.NotNull
@javax.annotation.Nonnull
@TypeQualifierDefault({ElementType.METHOD, ElementType.PARAMETER})
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Deprecated
@SuppressWarnings("unused")
public @interface NonNullApi {
}
