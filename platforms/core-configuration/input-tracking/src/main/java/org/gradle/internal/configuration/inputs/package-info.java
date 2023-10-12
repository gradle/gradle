/*
 * Copyright 2023 the original author or authors.
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
 * Configuration input tracking implementation.
 * <p>
 * The build configuration may be influenced by various "environmental" inputs.
 * For example, build logic may read files or access environment variables.
 * Knowing these inputs is important for configuration caching to understand if the cache has to be invalidated when the value of the system property changes.
 * It can also be used to determine undeclared task inputs, and the likes.
 */
package org.gradle.internal.configuration.inputs;
