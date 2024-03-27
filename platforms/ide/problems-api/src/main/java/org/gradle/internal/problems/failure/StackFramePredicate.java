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

public interface StackFramePredicate {

    StackFramePredicate USER_CODE = new ForRelevance(StackTraceRelevance.USER_CODE);
    StackFramePredicate INTERNAL_CODE = new ForRelevance(StackTraceRelevance.INTERNAL);

    boolean test(StackTraceElement frame, StackTraceRelevance relevance);

    class ForRelevance implements StackFramePredicate {

        private final StackTraceRelevance relevance;

        public ForRelevance(StackTraceRelevance relevance) {
            this.relevance = relevance;
        }

        @Override
        public boolean test(StackTraceElement frame, StackTraceRelevance relevance) {
            return this.relevance.equals(relevance);
        }
    }
}
