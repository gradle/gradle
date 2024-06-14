/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.problems.failure;

/**
 * Listener for steps in the process of printing a failure by {@link FailurePrinter}.
 */
public interface FailurePrinterListener {

    /**
     * Failure traversal action take after visiting a part of the failure.
     */
    enum VisitResult {
        /**
         * Continue visiting
         */
        CONTINUE,
        /**
         * Terminate visiting including all nested failures
         */
        TERMINATE,
    }

    FailurePrinterListener NO_OP = new FailurePrinterListener() {
        @Override
        public VisitResult beforeFrames() {
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult beforeFrame(StackTraceElement element, StackTraceRelevance relevance) {
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult afterFrames() {
            return VisitResult.CONTINUE;
        }
    };

    /**
     * Invoked after a failure header has been printed, and before any stack frames have been printed.
     */
    VisitResult beforeFrames();

    /**
     * Invoked before a given stack frame is printed.
     */
    VisitResult beforeFrame(StackTraceElement element, StackTraceRelevance relevance);

    /**
     * Invoked after all stack frames of a failure have been printed.
     */
    VisitResult afterFrames();

}
