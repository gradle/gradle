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

package org.gradle.tooling.internal.protocol.problem;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One element of a trace. Each kind of frame has a sub-interface; a client shows a kind it doesn't know through this one.
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * @since 9.10.0
 */
@NullMarked
public interface InternalFrame {

    String getDisplayName();

    /**
     * Where in a file the frame points, or null for a frame that isn't an authored unit or whose place isn't known.
     */
    @Nullable
    InternalPosition getPosition();

    /**
     * The locality as the name of its constant. A client maps a name it doesn't know to "unknown".
     */
    String getLocality();
}
