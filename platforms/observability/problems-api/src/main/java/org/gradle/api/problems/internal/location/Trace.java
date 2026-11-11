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

import com.google.common.collect.ImmutableList;

import java.io.Serializable;
import java.util.List;

/**
 * An ordered list of {@link Frame frames}, from the most specific one (the leaf) outward to the root.
 */
public final class Trace implements Serializable {

    // Declared as List, not ImmutableList: the plugin validation report serializes problems with Gson, which needs a type it can instantiate
    private final List<Frame> frames;

    public Trace(List<? extends Frame> frames) {
        this.frames = ImmutableList.copyOf(frames);
    }

    /**
     * The frames of this trace, leaf first.
     */
    public List<Frame> getFrames() {
        return frames;
    }

    /**
     * What this trace says about the cause itself: its authored frames, each by its name and position.
     * <p>
     * Two traces with the same authored frames name the same thing to fix, whatever build model units sit around them:
     * a field of a task type is one cause, however many tasks of that type there are. Locality is left out, since it is filled in after a problem is built.
     */
    public List<Object> getAuthoredIdentity() {
        ImmutableList.Builder<Object> identity = ImmutableList.builder();
        for (Frame frame : frames) {
            if (frame.isAuthored()) {
                identity.add(frame.getDisplayName());
                if (frame.getPosition() != null) {
                    identity.add(frame.getPosition());
                }
            }
        }
        return identity.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return frames.equals(((Trace) o).frames);
    }

    @Override
    public int hashCode() {
        return frames.hashCode();
    }

    @Override
    public String toString() {
        return frames.toString();
    }
}
