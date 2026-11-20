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

package org.gradle.internal.nullability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Defines a postcondition of a method: the method ensures that the specified fields aren't null upon normal exit.
/// NullAway verifies that when checking the annotated method. After calling the method, the field value is assumed to be non-null.
///
/// @see EnsuresNonNullIf
/// @see <a href="https://github.com/uber/NullAway/wiki/Supported-Annotations#field-contracts-precondition-and-postcondition">NullAway wiki</a>
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface EnsuresNonNull {
    /// A list of field names that must be non-null upon returning from the method. Must not be empty.
    ///
    /// You can only name fields of the receiver class and its superclasses: `foo` (for instance and static fields) or `this.foo` (for instance fields only).
    String[] value();
}
