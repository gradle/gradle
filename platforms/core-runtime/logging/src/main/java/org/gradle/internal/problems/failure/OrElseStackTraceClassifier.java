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

import javax.annotation.Nullable;

public class OrElseStackTraceClassifier implements StackTraceClassifier {

    private final StackTraceClassifier left;
    private final StackTraceClassifier right;

    public OrElseStackTraceClassifier(StackTraceClassifier left, StackTraceClassifier right) {
        this.left = left;
        this.right = right;
    }

    @Nullable
    @Override
    public StackTraceRelevance classify(StackTraceElement frame) {
        StackTraceRelevance leftRelevance = left.classify(frame);
        if (leftRelevance != null) {
            return leftRelevance;
        }

        return right.classify(frame);
    }
}
