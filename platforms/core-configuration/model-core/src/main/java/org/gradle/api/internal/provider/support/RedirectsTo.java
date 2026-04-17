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
 * Marks a legacy-named accessor as a redirect to the canonical lazy-property accessor
 * on the same class. The class generator synthesizes a body that delegates:
 * <ul>
 *   <li>Setter shape ({@code void set<LegacyName>(T)} or {@code DeclaringType set<LegacyName>(T)}):
 *       {@code ((LazyGroovySupport) get<value>()).setFromAnyValue(arg)}, autoboxing primitives.</li>
 *   <li>Getter shape ({@code L get<LegacyName>()} or {@code L is<LegacyName>()} where {@code L} is
 *       a lazy-property type): {@code return get<value>()}.</li>
 * </ul>
 *
 * <p>{@code value} is the canonical lazy property's name (e.g. {@code "destinationDirectory"}
 * when the canonical getter is {@code DirectoryProperty getDestinationDirectory()}).
 *
 * <p>Only needed when the accessor's name does not match the canonical getter's property
 * (e.g. {@code setDestinationDir} redirecting to {@code destinationDirectory}). Accessors
 * whose name already matches the canonical getter are handled by the existing
 * {@code PropertyMetadata} pairing and do not need this annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedirectsTo {
    String value();
}
