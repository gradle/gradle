/*
 * Copyright 2016 the original author or authors.
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
 * Provides classes, interfaces and annotations for registering and implementing artifact transforms.
 *
 * <p>
 *     To register an artifact transform, use {@link org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(java.lang.Class, org.gradle.api.Action)}.
 *     This allows you to register a {@link org.gradle.api.artifacts.transform.TransformAction} using {@link org.gradle.api.artifacts.transform.TransformSpec}.
 * </p>
 *
 * <p>If you want to create a new artifact transform action, have a look at {@link org.gradle.api.artifacts.transform.TransformAction}.</p>
 */
@org.gradle.api.NonNullApi
package org.gradle.api.artifacts.transform;
