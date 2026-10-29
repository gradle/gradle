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

/**
 * Classes for managing the lifecycle and operational scope of individual projects within a build.
 *
 * <p>
 * The mutable project model is represented internally by
 * {@link org.gradle.api.internal.project.ProjectState ProjectState} and exposed to build scripts via
 * {@link org.gradle.api.Project Project}, with
 * {@link org.gradle.api.internal.project.ProjectInternal ProjectInternal} providing additional internal
 * capabilities. {@link org.gradle.api.internal.project.ProjectStateRegistry ProjectStateRegistry} coordinates
 * project lifecycles across the build, while
 * {@link org.gradle.api.internal.project.CrossProjectModelAccess CrossProjectModelAccess} enforces stricter
 * cross-project access rules, primarily for Isolated Projects.
 */
@org.jspecify.annotations.NullMarked
package org.gradle.api.internal.project;
