/*
 * Copyright 2024 the original author or authors.
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

/**
 * Exceptions thrown when variant selection fails.
 *
 * The hierarchy of exceptions here should be kept small, and in sync with the 2
 * main branches of the {@link org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure ResolutionFailure}
 * hierarchy, which represent a failure to select a variant of a component, and a failure to select
 * a configuration by name.
 *
 * Artifact variant selection failures are not represented by a specific exception type, as they are
 * similar to graph selection failures, and the type of failure is more important that the type of
 * resolution being performed in this hierarchy.
 */
@NullMarked
package org.gradle.internal.component.resolution.failure.exception;

import org.jspecify.annotations.NullMarked;
