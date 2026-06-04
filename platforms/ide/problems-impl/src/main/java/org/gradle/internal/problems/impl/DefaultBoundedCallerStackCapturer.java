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

import org.gradle.internal.problems.BoundedCallerStackCapturer;
import org.gradle.internal.problems.failure.InternalStackTraceClassifier;
import org.gradle.internal.problems.failure.StackTraceClassifier;
import org.jspecify.annotations.Nullable;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Captures a bounded prefix of the calling thread's stack using {@link StackWalker}.
 */
public class DefaultBoundedCallerStackCapturer implements BoundedCallerStackCapturer {

    /**
     * Bounds the walk for the degenerate case of a stack with no user-code frame. Comfortably above
     * the depth-to-first-user-frame measured in real builds (~13 to 37).
     */
    private static final int MAX_DEPTH = 50;

    // MAX_DEPTH = buffer size hint: alloc once, no grow.
    private static final StackWalker WALKER = StackWalker.getInstance(EnumSet.noneOf(StackWalker.Option.class), MAX_DEPTH);
    private static final StackTraceClassifier INTERNAL_CLASSIFIER = new InternalStackTraceClassifier();

    @Nullable
    @Override
    public Throwable captureCallerStack() {
        StackTraceElement[] prefix = WALKER.walk(DefaultBoundedCallerStackCapturer::boundedPrefix);
        return prefix == null ? null : new BoundedStackHolder(prefix);
    }

    private static StackTraceElement @Nullable[] boundedPrefix(Stream<StackFrame> frames) {
        List<StackTraceElement> prefix = new ArrayList<>(MAX_DEPTH);
        boolean foundUserCode = false;
        Iterator<StackFrame> iterator = frames.iterator();
        for (int depth = 0; depth < MAX_DEPTH && iterator.hasNext(); depth++) {
            StackTraceElement element = iterator.next().toStackTraceElement();
            prefix.add(element);
            if (!foundUserCode) {
                if (isUserCode(element) && element.getLineNumber() >= 0) {
                    foundUserCode = true;
                }
            } else if (InternalStackTraceClassifier.isGradleCall(element.getClassName())) {
                // Include this Gradle boundary frame, matching the analyser's end position, then stop.
                break;
            }
        }
        return foundUserCode ? prefix.toArray(new StackTraceElement[0]) : null;
    }

    private static boolean isUserCode(StackTraceElement element) {
        return INTERNAL_CLASSIFIER.classify(element) == null;
    }

    /**
     * Carries an explicitly-set stack trace without paying the native {@code fillInStackTrace} cost,
     * by overriding it to a no-op. {@link #setStackTrace} still works because the trace is writable.
     */
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
