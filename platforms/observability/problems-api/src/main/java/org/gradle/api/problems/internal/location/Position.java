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

package org.gradle.api.problems.internal.location;

import org.jspecify.annotations.Nullable;

/**
 * Where in a file a frame points. Line and column count from 1, offset from 0; a missing value is null.
 */
public interface Position {

    @Nullable
    Integer getLine();

    @Nullable
    Integer getColumn();

    @Nullable
    Integer getOffset();

    @Nullable
    Integer getLength();
}
