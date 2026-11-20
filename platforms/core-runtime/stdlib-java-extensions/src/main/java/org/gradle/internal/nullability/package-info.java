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

/// Advanced nullability annotations to describe method contracts, pre- and post-conditions.
///
/// Intended to be consumed by [NullAway](https://github.com/uber/NullAway/wiki/Supported-Annotations).
/// This is basically a clean-room re-implementation of its own annotations that we cannot use because
/// they're Java 11, while we still target Java 8.
///
/// NullAway matches these annotations by simple name, so renaming them would break the checks.
@NullMarked
package org.gradle.internal.nullability;

import org.jspecify.annotations.NullMarked;
