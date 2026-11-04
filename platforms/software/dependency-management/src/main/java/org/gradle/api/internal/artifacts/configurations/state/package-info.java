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
 * State of a configuration that is only needed for some of its roles.
 * <p>
 * Each type holds the state for one role, so that a configuration only creates the state for the roles it is used in.
 */
@NullMarked
package org.gradle.api.internal.artifacts.configurations.state;

import org.jspecify.annotations.NullMarked;
