/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.declarative.dsl.model.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When applied to a member, makes the member visible in declarative definition if the type itself is hidden or is a hidden supertype.
 * The hidden member, therefore, appears in the subtypes of the declaring type. If the member is overridden elsewhere, it might be hidden again according to that declaration.
 * <p>
 * When applied to a type that gets hidden in the type hierarchy of another type (annotated as {@link HiddenInDefinition}), makes
 * the type annotated with {@link VisibleInDefinition} a visible type, making it available for use in declarative definitions again.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface VisibleInDefinition {}
