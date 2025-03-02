/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.artifacts.dsl;

/**
 * The supported lock modes:
 * <ul>
 *     <li>{@code DEFAULT} will load the lock state and verify resolution matches it</li>
 *     <li>{@code STRICT} in addition to the {@code DEFAULT} behaviour, will fail resolution if a locked configuration does not have lock state defined</li>
 *     <li>{@code LENIENT} will load the lock state, to anchor dynamic versions, but otherwise be lenient about modifications of the dependency resolution,
 *     allowing versions to change and module to be added or removed</li>
 * </ul>
 *
 * @since 6.1
 */
public enum LockMode {
    STRICT,
    DEFAULT,
    LENIENT
}
