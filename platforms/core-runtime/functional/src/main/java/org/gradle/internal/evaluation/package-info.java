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
 * Provides support for evaluating objects that offers features such as:
 * <ul>
 * <li>nested scopes</li>
 * <li>cycle detection to avoid infinite recursion</li>
 * <li>user-friendly error messages in the presence of cycles</li>
 * </ul>
 */
@NonNullApi
package org.gradle.internal.evaluation;

import org.gradle.api.NonNullApi;
