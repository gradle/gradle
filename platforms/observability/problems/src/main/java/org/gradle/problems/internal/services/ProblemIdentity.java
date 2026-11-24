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

package org.gradle.problems.internal.services;

import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.PropertyTraceData;
import org.gradle.api.problems.internal.location.Frame;
import org.gradle.api.problems.internal.location.PropertyFrame;
import org.gradle.api.problems.internal.location.TaskFrame;
import org.gradle.api.problems.internal.location.Trace;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * What makes two reported problems the same problem, so that the second can be dropped.
 * <p>
 * Two problems are the same when they say the same thing about the same cause. The cause is the flat origin locations and the authored
 * frames of the origin traces: the file line, the property, the class. Build model units, such as the task at the root of a trace,
 * don't make a problem distinct, and neither do the detection trace and the contextual locations: summarization keeps problems of
 * different tasks apart on its own.
 */
final class ProblemIdentity {

    private ProblemIdentity() {
    }

    /**
     * A key that is equal for two problems that are the same.
     */
    static Object of(ProblemInternal problem) {
        return Arrays.asList(
            problem.getDefinition(),
            problem.getContextualLabel(),
            problem.getSolutions(),
            problem.getOriginLocations(),
            authoredOrigins(problem.getOriginTraces()),
            problem.getDetails(),
            problem.getException(),
            identifyingData(problem),
            problem.isWrittenToConsole()
        );
    }

    private static List<List<Object>> authoredOrigins(List<Trace> originTraces) {
        ImmutableList.Builder<List<Object>> origins = ImmutableList.builder();
        for (Trace trace : originTraces) {
            origins.add(authoredIdentityOf(trace));
        }
        return origins.build();
    }

    /**
     * What a trace says about the cause itself: its authored frames, each by its name and position.
     * <p>
     * Two traces with the same authored frames name the same thing to fix, whatever build model units sit around them:
     * a field of a task type is one cause, however many tasks of that type there are. Locality is left out, since it doesn't change what is to fix.
     * <p>
     * A task counts when no type stands between it and the leaf: it is the leaf itself, or it follows a property directly.
     * The cause is then what one task was given, not what a type declares, so the same property name on another task is another cause.
     */
    static List<Object> authoredIdentityOf(Trace trace) {
        List<Frame> frames = trace.getFrames();
        ImmutableList.Builder<Object> identity = ImmutableList.builder();
        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            if (frame.isAuthored()) {
                identity.add(frame.getDisplayName());
                if (frame.getPosition() != null) {
                    identity.add(frame.getPosition());
                }
            } else if (frame instanceof TaskFrame && (i == 0 || frames.get(i - 1) instanceof PropertyFrame)) {
                identity.add(frame.getDisplayName());
            }
        }
        return identity.build();
    }

    // The text form of a Configuration Cache trace names the task that holds the state. It is a location, which the origin trace carries, so it doesn't identify the problem.
    private static @Nullable AdditionalData identifyingData(ProblemInternal problem) {
        AdditionalData data = problem.getAdditionalData();
        return data instanceof PropertyTraceData && !problem.getOriginTraces().isEmpty() ? null : data;
    }
}
