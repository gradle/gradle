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

package org.gradle.api.internal.provider.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a legacy-named accessor as backed by a canonical lazy-property getter on the
 * same class. The class generator synthesizes a body that delegates:
 * <ul>
 *   <li>Getter shape ({@code L getLegacy()} where {@code L} is a lazy-property type):
 *       {@code return <value>()}.</li>
 *   <li>Setter shape ({@code void setLegacy(T)} / {@code DeclaringType setLegacy(T)}):
 *       {@code ((LazyGroovySupport) <value>()).setFromAnyValue(arg)}, autoboxing primitives.</li>
 * </ul>
 *
 * <p>{@code value} is the canonical getter's method name (e.g. {@code "getDestinationDirectory"}),
 * so rename tooling catches it. The method must be declared on the same class and return a
 * lazy-property type.
 *
 * <p>Only needed when the accessor's name does not match the canonical getter's property
 * (e.g. {@code setDestinationDir} backed by {@code getDestinationDirectory}). Accessors
 * whose name already matches the canonical getter are paired automatically by the class
 * generator's property discovery and do not need this annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BackedByProperty {
    String value();
}
