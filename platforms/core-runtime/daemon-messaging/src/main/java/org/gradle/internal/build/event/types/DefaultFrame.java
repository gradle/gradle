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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.problem.InternalFrame;
import org.gradle.tooling.internal.protocol.problem.InternalPosition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

@NullMarked
public class DefaultFrame implements InternalFrame, Serializable {

    private final String displayName;
    private final String locality;
    @Nullable
    private final InternalPosition position;

    public DefaultFrame(String displayName, String locality) {
        this(displayName, locality, null);
    }

    public DefaultFrame(String displayName, String locality, @Nullable InternalPosition position) {
        this.displayName = displayName;
        this.locality = locality;
        this.position = position;
    }

    @Nullable
    @Override
    public InternalPosition getPosition() {
        return position;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getLocality() {
        return locality;
    }
}
