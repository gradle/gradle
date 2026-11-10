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

import org.gradle.internal.code.Locality;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * One element of a {@link Trace}: a build element a user recognises.
 * <p>
 * Every frame has a display name, a locality and, for authored units, a position in a file, so that a consumer can show a frame whose kind it doesn't know.
 * Subclasses add the data of their kind.
 */
public class Frame implements Serializable {

    private final String displayName;
    private final Locality locality;
    private final Position position;

    public Frame(String displayName, Locality locality) {
        this(displayName, locality, null);
    }

    public Frame(String displayName, Locality locality, @Nullable Position position) {
        this.displayName = displayName;
        this.locality = locality;
        this.position = position;
    }

    /**
     * Where in a file this frame points, if it is an authored unit with a known place.
     */
    @Nullable
    public Position getPosition() {
        return position;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Locality getLocality() {
        return locality;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Frame that = (Frame) o;
        return displayName.equals(that.displayName) && locality == that.locality && Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, locality, position);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
