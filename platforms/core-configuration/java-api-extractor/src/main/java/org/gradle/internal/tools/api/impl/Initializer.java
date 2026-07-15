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

package org.gradle.internal.tools.api.impl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that initializes {@code @NonNull} fields which are not set in the constructor.
 *
 * <p>NullAway treats fields assigned in an initializer method as initialized. This is used for ASM
 * visitor callbacks (such as {@code ClassVisitor.visit(...)}) that the framework guarantees to call
 * before the populated fields are read — something NullAway cannot infer from the constructor alone.
 *
 * <p>NullAway recognizes any annotation whose simple name is {@code Initializer}; see the
 * <a href="https://github.com/uber/NullAway/wiki/Supported-Annotations#initialization">NullAway documentation</a>.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
@interface Initializer {
}
