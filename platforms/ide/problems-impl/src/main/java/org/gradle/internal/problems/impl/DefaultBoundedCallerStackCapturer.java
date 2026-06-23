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

package org.gradle.internal.problems.impl;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.problems.BoundedCallerStackCapturer;
import org.gradle.internal.problems.failure.InternalStackTraceClassifier;
import org.jspecify.annotations.Nullable;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link StackWalker}-based capture of a reduced caller stack, used to infer a location past the stack
 * capture cap more cheaply than materialising a full stack trace.
 *
 * <p>The reduced stack keeps every frame from the top of the walk down to the <em>anchor</em> and drops
 * everything below it. The anchor is the frame the location resolves to: the first registered script the
 * walk reaches (the analyser sees through non-script frames to the calling script), or, when no script is
 * reached, the nearest user frame with a line. Keeping the frames above the anchor preserves the calling
 * context, the build-logic helper and the reporting entry point, without classifying which internal frames
 * matter, since there are several internal reporting paths. The frames below the anchor are pure Gradle
 * runtime and are dropped, which is also why the walk stops at the anchor.</p>
 *
 * <p>Each capture is resolved independently from its own live stack; nothing is cached or reused across
 * call sites, so a stack cannot be misattributed to another site's location. The {@link #MAX_DEPTH} cap and
 * the per-build budget on the number of captures are what keep the cost bounded.</p>
 */
class DefaultBoundedCallerStackCapturer implements BoundedCallerStackCapturer {

    // Walk cap when no anchor is reached. Above measured depth-to-script (~13-37 on gradle/gradle and nowinandroid).
    private static final int MAX_DEPTH = 50;

    // StackWalker buffer hint. Keep small: big hint fetches a whole batch up front, mostly wasted.
    private static final int ESTIMATE_DEPTH = 8;

    private static final StackWalker WALKER = StackWalker.getInstance(EnumSet.noneOf(StackWalker.Option.class), ESTIMATE_DEPTH);

    private final RegisteredScripts registeredScripts;

    DefaultBoundedCallerStackCapturer(RegisteredScripts registeredScripts) {
        this.registeredScripts = registeredScripts;
    }

    @Nullable
    @Override
    public Throwable captureCallerStack() {
        StackTraceElement[] reducedStack = WALKER.walk(this::reduceStack);
        return reducedStack == null ? null : new BoundedStackHolder(reducedStack);
    }

    @VisibleForTesting
    StackTraceElement @Nullable [] reduceStack(Stream<StackFrame> frames) {
        BoundedWalk walk = new BoundedWalk();
        Iterator<StackFrame> iterator = frames.iterator();
        for (int depth = 0; depth < MAX_DEPTH && iterator.hasNext(); depth++) {
            if (walk.accept(iterator.next())) {
                break; // reached the anchor or the Gradle boundary below the user frames
            }
        }
        return walk.reducedStack();
    }

    private boolean isRegisteredScript(StackTraceElement element) {
        String fileName = element.getFileName();
        return fileName != null && registeredScripts.scriptFor(fileName) != null;
    }

    /**
     * A single bounded walk: collects frames from the top down to the anchor, tracking the anchor the
     * location resolves to (the first registered script, else the nearest user frame with a line).
     */
    private final class BoundedWalk {
        private final List<StackTraceElement> frames = new ArrayList<>(MAX_DEPTH);
        private int locatedIndex = -1; // nearest user frame with a line: the fallback anchor
        private int scriptIndex = -1;  // first registered script: the anchor the location resolves to
        private boolean sawUserFrame;

        /**
         * @return true once the cut point is known and the walk can stop
         */
        boolean accept(StackFrame frame) {
            String className = frame.getClassName();
            if (sawUserFrame && InternalStackTraceClassifier.isGradleCall(className)) {
                return true; // Gradle boundary below the user frames: no script will follow, drop it and the rest
            }
            StackTraceElement element = frame.toStackTraceElement();
            frames.add(element);
            if (InternalStackTraceClassifier.isInternal(className)) {
                return false; // reporting and dispatch machinery above the user code: kept for context, never an anchor
            }
            sawUserFrame = true;
            if (element.getLineNumber() < 0) {
                return false; // synthetic user frame without a line cannot be a location
            }
            if (locatedIndex == -1) {
                locatedIndex = frames.size() - 1;
            }
            if (isRegisteredScript(element)) {
                scriptIndex = frames.size() - 1;
                return true; // anchor: stop here, the location resolves to this script
            }
            return false;
        }

        StackTraceElement @Nullable [] reducedStack() {
            int anchor = scriptIndex != -1 ? scriptIndex : locatedIndex;
            if (anchor == -1) {
                return null; // no user frame with a location
            }
            return frames.subList(0, anchor + 1).toArray(new StackTraceElement[0]);
        }
    }

    // Holds a stack trace, skips costly native fillInStackTrace
    private static final class BoundedStackHolder extends Exception {
        BoundedStackHolder(StackTraceElement[] stackTrace) {
            super();
            setStackTrace(stackTrace);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
