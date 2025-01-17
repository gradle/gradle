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
 * This package contains types for automatically resolving and loading plugins based on arguments to a build.
 * <p>
 * This is currently used by the {@code init} task, but is implemented as a general-purpose plugin loading mechanism.
 */
@org.gradle.api.NonNullApi
package org.gradle.plugin.management.internal.argumentloaded;
